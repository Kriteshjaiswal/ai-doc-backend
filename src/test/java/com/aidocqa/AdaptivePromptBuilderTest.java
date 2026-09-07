package com.aidocqa;

import com.aidocqa.entity.ChatHistory;
import com.aidocqa.service.AdaptivePromptBuilder;
import com.aidocqa.service.AdaptivePromptBuilder.LengthConstraint;
import com.aidocqa.service.AdaptivePromptBuilder.PromptBundle;
import com.aidocqa.service.AdaptivePromptBuilder.QuestionComplexity;
import com.aidocqa.service.AdaptivePromptBuilder.UserIntent;
import com.aidocqa.service.GeminiApiService;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class AdaptivePromptBuilderTest {

    private final AdaptivePromptBuilder promptBuilder = new AdaptivePromptBuilder();
    private final GeminiApiService geminiApiService = new GeminiApiService(new RestTemplate(), promptBuilder);

    @Test
    public void testPromptStructureWithXmlDelimiters() {
        String docText = "PostgreSQL runs by default on port 5432.";
        String question = "What is the default PostgreSQL port?";
        List<ChatHistory> history = List.of(
                ChatHistory.builder().question("Which database are we using?").answer("PostgreSQL database.").build()
        );

        PromptBundle bundle = promptBuilder.buildPromptBundle(docText, null, question, history);

        assertNotNull(bundle);
        assertNotNull(bundle.systemInstruction());
        assertNotNull(bundle.userPrompt());

        // Verify XML delimiters
        assertTrue(bundle.userPrompt().contains("<DOCUMENT_CONTEXT>"));
        assertTrue(bundle.userPrompt().contains("</DOCUMENT_CONTEXT>"));
        assertTrue(bundle.userPrompt().contains("<USER_QUESTION>"));
        assertTrue(bundle.userPrompt().contains("</USER_QUESTION>"));
        assertTrue(bundle.userPrompt().contains("<CONVERSATION_HISTORY>"));
        assertTrue(bundle.userPrompt().contains("</CONVERSATION_HISTORY>"));

        // Verify that system instruction is lean and does not contain legacy 24-section bloat
        assertFalse(bundle.systemInstruction().contains("## 24. FINAL INTERNAL QUALITY CHECK"));
        assertFalse(bundle.systemInstruction().contains("MASTER SYSTEM PROMPT — High-Quality Grounded AI Response Engine"));

        // Verify zero meta-text directives
        assertTrue(bundle.systemInstruction().contains("ZERO META-TEXT"));
        assertTrue(bundle.systemInstruction().contains("Ready. Please provide"));
        assertTrue(bundle.systemInstruction().contains("Generate only the amount of text required to completely and accurately answer the question."));
    }

    @Test
    public void testComplexityClassification() {
        // Simple
        QuestionComplexity simpleComp = promptBuilder.classifyComplexity(
                "What is the port?", UserIntent.FACTUAL_SHORT, LengthConstraint.SHORT_CONCISE);
        assertEquals(QuestionComplexity.SIMPLE, simpleComp);

        // Moderate
        QuestionComplexity modComp = promptBuilder.classifyComplexity(
                "How to configure Spring Boot security?", UserIntent.STEP_BY_STEP_HOWTO, LengthConstraint.BALANCED_STANDARD);
        assertEquals(QuestionComplexity.MODERATE, modComp);

        // Complex
        QuestionComplexity compComp = promptBuilder.classifyComplexity(
                "Compare Factory Method vs Abstract Factory design patterns in detail",
                UserIntent.COMPARISON, LengthConstraint.BALANCED_STANDARD);
        assertEquals(QuestionComplexity.COMPLEX, compComp);
    }

    @Test
    public void testSanitizeAnswerStripsMetaText() {
        // 1. Exact problem phrase reported by user
        String bad1 = "Ready. Please provide the document context and your question to begin.";
        assertEquals("", geminiApiService.sanitizeAnswer(bad1));

        // 2. Bad phrase followed by actual answer
        String bad2 = "Ready. Please provide the document context and your question to begin.\n\nThe default port is 5432.";
        assertEquals("The default port is 5432.", geminiApiService.sanitizeAnswer(bad2));

        // 3. Conversational filler
        String bad3 = "Sure! I would be glad to explain. The result is 42.";
        assertEquals("The result is 42.", geminiApiService.sanitizeAnswer(bad3));

        // 4. Repeated question
        String bad4 = "Question: What is the capital?\nAnswer: Paris.";
        assertEquals("Paris.", geminiApiService.sanitizeAnswer(bad4));

        // 5. Normal direct answer is preserved
        String good = "PostgreSQL is an open-source relational database.";
        assertEquals(good, geminiApiService.sanitizeAnswer(good));
    }

    @Test
    public void testDynamicDepthDetectionShortSummaryToShortReply() {
        // "short summary" -> LEVEL_1_QUICK
        String q1 = "Give a short summary of Singleton pattern in short reply";
        PromptBundle bundle1 = promptBuilder.buildPromptBundle("Doc text", null, q1, null);
        assertEquals(AdaptivePromptBuilder.DepthLevel.LEVEL_1_QUICK, bundle1.depthLevel());
        assertTrue(bundle1.maxOutputTokens() <= 1536);
        assertTrue(bundle1.systemInstruction().contains("[ACTIVE DEPTH: LEVEL 1 - QUICK / SHORT REPLY]"));

        // "kewal naam" -> LEVEL_1_QUICK
        String q2 = "Design patterns ke kewal naam batao";
        PromptBundle bundle2 = promptBuilder.buildPromptBundle("Doc text", null, q2, null);
        assertEquals(AdaptivePromptBuilder.DepthLevel.LEVEL_1_QUICK, bundle2.depthLevel());
    }

    @Test
    public void testDynamicDepthDetectionMoreExplainToNormalOrDetailed() {
        // "more explain" / "more explane" -> LEVEL_2_NORMAL (User explicit rule: "2-> more explane so normal")
        String q1 = "Factory pattern ko more explane kro";
        PromptBundle bundle1 = promptBuilder.buildPromptBundle("Doc text", null, q1, null);
        assertEquals(AdaptivePromptBuilder.DepthLevel.LEVEL_2_NORMAL, bundle1.depthLevel());
        assertTrue(bundle1.systemInstruction().contains("[ACTIVE DEPTH: LEVEL 2 - NORMAL / BALANCED EXPLANATION]"));

        // "properly with example" / "detail mein" -> LEVEL_3_DETAILED
        String q2 = "Explain Singleton pattern properly with example";
        PromptBundle bundle2 = promptBuilder.buildPromptBundle("Doc text", null, q2, null);
        assertEquals(AdaptivePromptBuilder.DepthLevel.LEVEL_3_DETAILED, bundle2.depthLevel());
    }

    @Test
    public void testDynamicDepthDetectionOrMoreAndComplexScenariosToDeepMasterclass() {
        // "or more" / "deeply explain" / "complex scenarios"
        String q1 = "Singleton pattern or more detail deeply explain step by step samjhao for complex scenarios";
        PromptBundle bundle1 = promptBuilder.buildPromptBundle("Doc text", null, q1, null);
        assertEquals(AdaptivePromptBuilder.DepthLevel.LEVEL_4_DEEP, bundle1.depthLevel());
        assertEquals(8192, bundle1.maxOutputTokens());
        assertTrue(bundle1.systemInstruction().contains("[ACTIVE DEPTH: LEVEL 4 - DEEP TUTORIAL / 'OR MORE' / MASTERCLASS]"));
        assertTrue(bundle1.systemInstruction().contains("PRODUCTION-GRADE CODE"));
        assertTrue(bundle1.systemInstruction().contains("COMMON CONFUSION CLARIFICATION"));
    }

    @Test
    public void testParameterExtractionExplicitLinesAndHinglish() {
        String q = "Explain Factory pattern in 4-5 lines mujhe samjhao";
        PromptBundle bundle = promptBuilder.buildPromptBundle("Doc text", null, q, null);
        assertTrue(bundle.systemInstruction().contains("[CRITICAL CONSTRAINT]: The user explicitly requested: '4-5 lines'"));
        assertTrue(bundle.systemInstruction().contains("[LANGUAGE]: The user communicates in Hindi/Hinglish"));
    }

    @Test
    public void testExplicitDepthOverrideLowMediumHigh() {
        String question = "What is dependency injection?";

        // 1. Explicit LOW
        PromptBundle lowBundle = promptBuilder.buildPromptBundle("Doc text", null, question, null, "LOW");
        assertEquals(AdaptivePromptBuilder.DepthLevel.LEVEL_1_QUICK, lowBundle.depthLevel());
        assertTrue(lowBundle.maxOutputTokens() <= 1536);
        assertTrue(lowBundle.systemInstruction().contains("[LOW / QUICK MODE]"));

        // 2. Explicit MEDIUM
        PromptBundle medBundle = promptBuilder.buildPromptBundle("Doc text", null, question, null, "MEDIUM");
        assertEquals(AdaptivePromptBuilder.DepthLevel.LEVEL_2_NORMAL, medBundle.depthLevel());
        assertEquals(4096, medBundle.maxOutputTokens());
        assertTrue(medBundle.systemInstruction().contains("[MEDIUM / BALANCED MODE]"));

        // 3. Explicit HIGH
        PromptBundle highBundle = promptBuilder.buildPromptBundle("Doc text", null, question, null, "HIGH");
        assertEquals(AdaptivePromptBuilder.DepthLevel.LEVEL_4_DEEP, highBundle.depthLevel());
        assertEquals(8192, highBundle.maxOutputTokens());
        assertTrue(highBundle.systemInstruction().contains("[HIGH / COMPLEX TOP-TIER MODE]"));
    }
}


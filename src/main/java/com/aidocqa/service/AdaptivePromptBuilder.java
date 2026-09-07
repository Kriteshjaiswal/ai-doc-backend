package com.aidocqa.service;

import com.aidocqa.entity.ChatHistory;
import lombok.Getter;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Intelligent Adaptive Prompt Engine & Parameter Detection System.
 *
 * Implements the 21-Principle Intelligence Framework:
 * 1. Intent Detection across 17+ granular intents (Short factual, conceptual, deep masterclass, how-to, debugging, comparison, interview prep, etc.).
 * 2. 4-Tier Dynamic Depth Scaling (Level 1 Quick, Level 2 Normal, Level 3 Detailed, Level 4 Deep Tutorial / "Or More").
 * 3. Exact Reply Tailoring: Short summary -> crisp short reply; More explain -> balanced normal; Or more / complex -> comprehensive deep masterclass.
 * 4. Strict Constraint & Parameter Extraction (explicit line/bullet counts, Hinglish detection, Java/Spring domain cues).
 * 5. Strict Zero Meta-Text (No robotic preambles, no "Ready. Please provide", no question repeating).
 * 6. Document Grounding with Safe Educational Synthesis.
 */
@Component
public class AdaptivePromptBuilder {

    public enum UserIntent {
        FACTUAL_SHORT,
        DEFINITION,
        CONCEPT_EXPLANATION,
        DEEP_EXPLANATION,
        SUMMARY,
        STEP_BY_STEP_HOWTO,
        COMPARISON,
        ANALYSIS_REASONING,
        LIST_EXTRACTION,
        CODE_IMPLEMENTATION,
        CODE_DEBUG_FIX,
        DESIGN_PATTERNS,
        INTERVIEW_PREPARATION,
        ARCHITECTURE,
        DOCUMENT_GROUNDED_QA,
        CONVERSATIONAL_FOLLOWUP,
        GENERAL_QA
    }

    public enum DepthLevel {
        LEVEL_1_QUICK("Level 1 (Quick / Direct Lookup / Short Summary)"),
        LEVEL_2_NORMAL("Level 2 (Normal / Balanced Explanation)"),
        LEVEL_3_DETAILED("Level 3 (Detailed / Progressive Explanation)"),
        LEVEL_4_DEEP("Level 4 (Deep Masterclass / Step-by-Step Tutorial / 'Or More')");

        @Getter
        private final String description;

        DepthLevel(String description) {
            this.description = description;
        }
    }

    public enum LengthConstraint {
        ONE_LINER(1024),
        EXPLICIT_FEW_LINES(1536),
        SHORT_CONCISE(1536),
        BALANCED_STANDARD(4096),
        DETAILED_DEPTH(8192),
        UNCONSTRAINED(8192);

        @Getter
        private final int estimatedMaxTokens;

        LengthConstraint(int estimatedMaxTokens) {
            this.estimatedMaxTokens = estimatedMaxTokens;
        }
    }

    public enum QuestionComplexity {
        SIMPLE,
        MODERATE,
        COMPLEX
    }

    public record PromptBundle(
            String systemInstruction,
            String userPrompt,
            int maxOutputTokens,
            double temperature,
            UserIntent intent,
            LengthConstraint lengthConstraint,
            QuestionComplexity complexity,
            DepthLevel depthLevel
    ) {
        public PromptBundle(
                String systemInstruction,
                String userPrompt,
                int maxOutputTokens,
                double temperature,
                UserIntent intent,
                LengthConstraint lengthConstraint,
                QuestionComplexity complexity
        ) {
            this(systemInstruction, userPrompt, maxOutputTokens, temperature, intent, lengthConstraint, complexity, DepthLevel.LEVEL_2_NORMAL);
        }

        public PromptBundle(
                String systemInstruction,
                String userPrompt,
                int maxOutputTokens,
                double temperature,
                UserIntent intent,
                LengthConstraint lengthConstraint
        ) {
            this(systemInstruction, userPrompt, maxOutputTokens, temperature, intent, lengthConstraint, QuestionComplexity.MODERATE, DepthLevel.LEVEL_2_NORMAL);
        }
    }

    /**
     * Constructs a decoupled System Instruction + User Context bundle for Gemini API.
     */
    public PromptBundle buildPromptBundle(
            String documentText,
            List<String> pageImagesBase64,
            String question,
            List<ChatHistory> recentHistory
    ) {
        return buildPromptBundle(documentText, pageImagesBase64, question, recentHistory, null);
    }

    /**
     * Constructs a decoupled System Instruction + User Context bundle with optional explicit depth control (LOW, MEDIUM, HIGH).
     */
    public PromptBundle buildPromptBundle(
            String documentText,
            List<String> pageImagesBase64,
            String question,
            List<ChatHistory> recentHistory,
            String explicitDepth
    ) {
        String cleanQ = question != null ? question.trim() : "";
        boolean isDocumentContext = (documentText != null && !documentText.isBlank()) ||
                                    (pageImagesBase64 != null && !pageImagesBase64.isEmpty());

        // 1. Parameter Extraction & Detection
        UserIntent intent = classifyIntent(cleanQ, isDocumentContext);
        boolean isHinglish = detectHinglish(cleanQ);
        LengthConstraint lengthConstraint = detectLengthConstraint(cleanQ, intent);
        String explicitLineConstraint = extractExplicitLineConstraint(cleanQ);
        QuestionComplexity complexity = classifyComplexity(cleanQ, intent, lengthConstraint);

        // Explicit Depth Override (LOW, MEDIUM, HIGH)
        DepthLevel depthLevel;
        if (explicitDepth != null && !explicitDepth.isBlank()) {
            depthLevel = switch (explicitDepth.trim().toUpperCase()) {
                case "LOW" -> {
                    lengthConstraint = LengthConstraint.SHORT_CONCISE;
                    yield DepthLevel.LEVEL_1_QUICK;
                }
                case "HIGH" -> {
                    lengthConstraint = LengthConstraint.DETAILED_DEPTH;
                    complexity = QuestionComplexity.COMPLEX;
                    yield DepthLevel.LEVEL_4_DEEP;
                }
                case "MEDIUM" -> {
                    lengthConstraint = LengthConstraint.BALANCED_STANDARD;
                    yield DepthLevel.LEVEL_2_NORMAL;
                }
                default -> classifyDepthLevel(cleanQ, intent, lengthConstraint);
            };
        } else {
            depthLevel = classifyDepthLevel(cleanQ, intent, lengthConstraint);
        }

        boolean isJavaOrSpring = detectJavaOrSpringContext(cleanQ);
        boolean asksForPages = cleanQ.toLowerCase().contains("page") ||
                               cleanQ.toLowerCase().contains("reference") ||
                               cleanQ.toLowerCase().contains("kaha par") ||
                               cleanQ.toLowerCase().contains("kaha se");

        // 2. Build Intelligent System Instruction with Intent & Depth Adaptation
        String systemInstruction = buildSystemInstruction(
                intent,
                complexity,
                depthLevel,
                lengthConstraint,
                isHinglish,
                isDocumentContext,
                explicitLineConstraint,
                isJavaOrSpring,
                asksForPages
        );

        // 3. Build Structured User Content Prompt with XML Delimiters
        StringBuilder userPrompt = new StringBuilder();

        // Multi-Turn Conversation Context (bounded to last 3 turns)
        if (recentHistory != null && !recentHistory.isEmpty()) {
            List<ChatHistory> chronological = new ArrayList<>(recentHistory);
            Collections.reverse(chronological);

            int historyCount = Math.min(chronological.size(), 3);
            if (historyCount > 0) {
                userPrompt.append("<CONVERSATION_HISTORY>\n");
                for (int i = chronological.size() - historyCount; i < chronological.size(); i++) {
                    ChatHistory h = chronological.get(i);
                    userPrompt.append("User: ").append(h.getQuestion()).append("\n");
                    String ans = h.getAnswer();
                    if (ans != null && ans.length() > 300) {
                        ans = ans.substring(0, 300) + "...";
                    }
                    userPrompt.append("Assistant: ").append(ans).append("\n");
                }
                userPrompt.append("</CONVERSATION_HISTORY>\n\n");
            }
        }

        // Document Context (RAG)
        if (isDocumentContext) {
            userPrompt.append("<DOCUMENT_CONTEXT>\n");
            userPrompt.append(documentText != null && !documentText.isBlank() ? documentText.trim() : "Document pages provided as multimodal images.");
            userPrompt.append("\n</DOCUMENT_CONTEXT>\n\n");
        }

        // User Question
        userPrompt.append("<USER_QUESTION>\n");
        userPrompt.append(cleanQ);
        userPrompt.append("\n</USER_QUESTION>");

        // Dynamic Temperature based on Intent and Depth
        double temp = switch (intent) {
            case FACTUAL_SHORT, LIST_EXTRACTION, CODE_DEBUG_FIX -> 0.2;
            case COMPARISON, STEP_BY_STEP_HOWTO, DESIGN_PATTERNS -> 0.25;
            case SUMMARY, CONCEPT_EXPLANATION, DEFINITION -> 0.3;
            case DEEP_EXPLANATION, INTERVIEW_PREPARATION, ARCHITECTURE -> 0.35;
            default -> 0.35;
        };

        // Determine token ceiling according to depth level
        int tokens = switch (depthLevel) {
            case LEVEL_1_QUICK -> 1024;
            case LEVEL_2_NORMAL -> 4096;
            case LEVEL_3_DETAILED -> 6144;
            case LEVEL_4_DEEP -> 8192;
        };

        return new PromptBundle(
                systemInstruction,
                userPrompt.toString(),
                tokens,
                temp,
                intent,
                lengthConstraint,
                complexity,
                depthLevel
        );
    }

    /**
     * Backward-compatible single-prompt string builder.
     */
    public String buildAdaptivePrompt(
            String documentText,
            List<String> pageImagesBase64,
            String question,
            List<ChatHistory> recentHistory
    ) {
        PromptBundle bundle = buildPromptBundle(documentText, pageImagesBase64, question, recentHistory, null);
        return "=== SYSTEM INSTRUCTION ===\n" + bundle.systemInstruction() +
               "\n\n=== USER INPUT & CONTEXT ===\n" + bundle.userPrompt();
    }

    private String buildSystemInstruction(
            UserIntent intent,
            QuestionComplexity complexity,
            DepthLevel depthLevel,
            LengthConstraint lengthConstraint,
            boolean isHinglish,
            boolean isDocumentContext,
            String explicitLineConstraint,
            boolean isJavaOrSpring,
            boolean asksForPages
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append("""
# GOOGLE GEMINI FLASH DOCUMENT Q&A & SMART REASONING ENGINE

You are an expert AI document assistant, technical tutor, and reasoning-based question answering system.
Your core principle:
USER INTENT > DOCUMENT RELEVANCE > ANSWER QUALITY > EXTRA INFORMATION

## ANSWERING & ZERO META-TEXT RULES
* Answer the user's question directly. Do not restate or repeat the question.
* Never include pleasantries, greetings, or readiness statements (e.g. do NOT say "Ready. Please provide...", "I am ready to help", "Sure", "Certainly", "Let's begin", "Based on your request", or "As an AI").
* Do not explain that you are an assistant or describe your internal reasoning process.
* For complex questions, perform all required reasoning internally, outputting only the useful conclusion and supporting explanation. Never expose chain-of-thought or hidden analysis.
* Generate only the amount of text required to completely and accurately answer the question.

## READABILITY & FORMATTING RULES (PARAGRAPHS FIRST, MINIMAL BULLETS)
* WRITE IN NATURAL, COHESIVE PARAGRAPHS: Always explain concepts, background, reasoning, and context in natural, well-formed paragraphs so the user can easily follow the sentence flow and line context. Do NOT convert every single sentence or thought into a bullet point.
* BULLET POINTS STRICTLY FOR LISTS ONLY: Use bullet points sparingly — ONLY when presenting a distinct list of 3 or more discrete items (such as a list of tool names, specific design pattern names, or interview problem names).
* NEVER USE BULLETS FOR HEADINGS, EXPLANATIONS, OR TAKEAWAYS: Explanations, architectural principles, definitions, and Key Takeaways must be written in normal narrative paragraphs, NOT as bullet points.
* AVOID BULLET STORMS: An answer should feel like a clear, readable technical guide or explanation, not an overwhelming list of bullet points.

## DOCUMENT GROUNDING & SYNTHESIS
* Context provided inside <DOCUMENT_CONTEXT> is the PRIMARY SOURCE OF TRUTH.
* Do not invent or hallucinate document-specific facts, numbers, dates, or confidential terms.
* When the user's question relates to document concepts (e.g. Design Patterns, Java architecture, best practices) and also asks for broader educational context, YouTube channels, learning references, or practical industry examples, synthesize the document's concepts with authoritative technical knowledge to provide a comprehensive, fully helpful response.
* If a question asks strictly about document-specific data (figures, metrics, policies) and the context does not contain it, state clearly: "The available document context does not contain sufficient information to answer this question."
""");

        // Dynamic Depth Level Directives
        sb.append("\n## ADAPTIVE DEPTH & TEACHING SPECIFICATION\n");
        switch (depthLevel) {
            case LEVEL_1_QUICK -> sb.append("""
[ACTIVE DEPTH: LEVEL 1 - LOW / QUICK MODE (CONDENSED DOWN-RESPONSE OF MEDIUM)]
* [LOW MODE DEFINITION]: Deliver a high-signal, condensed 'down-response' of the medium explanation.
* Length & Format: Exactly 1-2 focused, cohesive paragraphs (or 3-5 concise bullet points if a list is strictly requested).
* Content: Directly answers the core question, explains the primary mechanism simply, and provides the bottom-line takeaway.
* Tone & Style: Crisp, beginner-friendly, and crystal clear. Avoid deep sub-sections, lengthy theory, or code blocks.
* Zero conversational padding, zero filler, zero unrequested theory. Stop as soon as the question is satisfied.
""");
            case LEVEL_2_NORMAL -> sb.append("""
[ACTIVE DEPTH: LEVEL 2 - MEDIUM / BALANCED BUT STRONG]
* [MEDIUM MODE DEFINITION]: Deliver a balanced, highly authoritative, and technically strong response:
  1. Direct Architecture / Concept Foundation (in 1-2 fluent explanatory paragraphs)
  2. Core Mechanics & How It Works (structured cleanly with descriptive ### Markdown subheadings)
  3. Practical Application & Real-World Production Context (clear, focused technical explanation)
  4. Key Takeaways & Considerations (definitive concluding paragraph)
* Technical Strength: Authoritative and robust, ensuring high accuracy while remaining balanced and readable.
* When multiple sub-topics are asked, explain each one clearly and concisely with focused scope.
* Do NOT wrap explanations or takeaways into bullet points. Use bullets only if listing specific concrete items.
""");
            case LEVEL_3_DETAILED -> sb.append("""
[ACTIVE DEPTH: LEVEL 3 - DETAILED EXPLANATION]
* The user asked for a detailed explanation ('detail mein', 'achhe se', 'properly explain', 'more explain', 'explain with example').
* Teach the concept progressively using clear Markdown headers (###) and coherent explanatory paragraphs:
  1. What it is and why it exists (explain in natural paragraphs)
  2. Problem with the naive approach
  3. Core mechanics & step-by-step working
  4. Practical code / real-world example with walkthrough
  5. Trade-offs and key considerations
* Maintain fluent paragraph explanations. Avoid bullet point overload; reserve bullets strictly for concise item lists or feature enumerations.
""");
            case LEVEL_4_DEEP -> sb.append("""
[ACTIVE DEPTH: LEVEL 4 - HIGH / MOST POWERFUL 98%-100% ACCURACY MASTERCLASS]
* [HIGH MODE DEFINITION]: Deliver the absolute strongest, definitive, top-tier response that achieves 98%-100% technical accuracy, architectural rigor, and uncompromising depth.
* COGNITIVE & TEACHING STANDARD:
  - Write with the authoritative precision, clarity, and depth of a World-Class Principal Staff Engineer & Technical Author.
  - Zero hand-waving, zero superficial summaries, zero textbook platitudes. Start directly with a sharp, high-signal conceptual answer and intuitive mental model.
* COMPLETE MULTI-TOPIC COVERAGE & MANDATORY CLOSURE:
  - When the user's prompt lists multiple specific topics or requirements, you MUST systematically cover EVERY SINGLE ONE under its own dedicated Markdown section (###).
  - Pace your explanation evenly across all items. Never let earlier topics crowd out later topics.
  - Keep code examples focused, concise, and high-signal (20-35 lines max per snippet) to illustrate key logic, ensuring ample capacity to completely explain all subsequent sections, diagrams, and trade-off analyses.
  - Ensure the response reaches a clean, definitive conclusion that covers the final requested points (e.g., duplicate execution, idempotency, worker crash, and exactly-once vs at-least-once execution semantics).
* 5 PILLARS OF A 98%-100% BENCHMARK RESPONSE:
  1. First-Principles & Architectural 'Why':
     - Explain the fundamental engineering bottleneck, race condition, or failure mode it solves and how it executes under the hood (memory model, consensus protocol, network partitioning, thread lifecycle).
  2. Concrete Production Architecture & Real-World Reality:
     - Ground the explanation in production realities (distributed systems, high concurrency, persistence layers, network partitioning).
  3. PRODUCTION-GRADE CODE & Concrete Implementation:
     - Focused, production-hardened snippets with thread-safety, proper exception handling, and clean naming.
  4. Subtle Pitfalls, Anti-Patterns & Edge Cases:
     - Expose subtle traps: race conditions, fencing token violations, split-brain, memory leaks, and failure recovery.
  5. COMMON CONFUSION CLARIFICATION & Trade-Off Matrix:
     - Directly resolve tricky trade-offs (e.g., at-least-once vs exactly-once semantics, push vs pull workers).
* WRITING STYLE:
  - Write in natural, cohesive paragraphs with contextual markdown subheadings (###).
  - Avoid bullet point overload; reserve bullets strictly for concise item lists or feature enumerations.
""");
        }

        // Intent-Specific Structure Guidance
        sb.append("\n## INTENT-SPECIFIC STRUCTURE\n");
        switch (intent) {
            case COMPARISON -> sb.append("""
* For COMPARISON: Briefly introduce both concepts, then provide a clear Markdown Comparison Table comparing: Purpose, Problem Solved, Runtime Behavior, Coupling, and Code Structure. Conclude with explicit 'When to choose A' vs 'When to choose B'.
""");
            case CODE_IMPLEMENTATION -> sb.append("""
* For CODE IMPLEMENTATION: Explain the approach, show clean modern code, explain important lines and execution flow, and highlight edge cases and concurrency/thread-safety.
""");
            case CODE_DEBUG_FIX -> sb.append("""
* For TROUBLESHOOTING & DEBUGGING: State the most likely root cause, explain why it happens, provide verification steps, give the step-by-step fix, and explain prevention.
""");
            case INTERVIEW_PREPARATION -> sb.append("""
* For INTERVIEW PREPARATION: Focus on high-impact concept clarity, one-line interview definition, real-world example, common confusion, and tricky follow-up questions.
""");
            case SUMMARY -> sb.append("""
* For SUMMARY: Summarize strictly the requested material cleanly without unnecessary meta-text.
""");
            default -> {}
        }

        // Technical Accuracy Rules (Java & Spring)
        if (isJavaOrSpring) {
            sb.append("""
\n## JAVA & ENTERPRISE ARCHITECTURE ACCURACY
* Maintain strict technical accuracy: Clearly distinguish canonical GoF design patterns from Spring Framework features (e.g. Spring @Component singleton scope is bean lifecycle management by Spring IoC container, not the GoF Singleton pattern).
* In Java concurrency/threading questions, accurately explain memory visibility (volatile), synchronization, race conditions, and thread safety.
* Always show clean, modern Java using interfaces, loose coupling, and meaningful naming.
""");
        }

        // Explicit Constraints
        if (explicitLineConstraint != null) {
            sb.append("\n[CRITICAL CONSTRAINT]: The user explicitly requested: '")
              .append(explicitLineConstraint)
              .append("'. Strictly obey this length limit.\n");
        } else if (lengthConstraint == LengthConstraint.ONE_LINER) {
            sb.append("\n[CONSTRAINT]: Deliver a single-line or single-sentence answer only.\n");
        } else if (lengthConstraint == LengthConstraint.SHORT_CONCISE) {
            sb.append("\n[CONSTRAINT]: Deliver a concise answer (2-4 sentences or compact list) without unnecessary padding.\n");
        }

        // Hinglish / Hindi Language Matching
        if (isHinglish) {
            sb.append("\n[LANGUAGE]: The user communicates in Hindi/Hinglish. Respond naturally in fluent technical Hinglish, keeping all standard technical terminology, Java keywords, class names, and code strictly in English.\n");
        }

        // Page Citations & Strict Zero Page Numbers Rule
        if (asksForPages && isDocumentContext) {
            sb.append("\n[PAGE CITATIONS]: The user explicitly asked for page references. Cite verified page numbers from the document context at the very end of your response. Never invent page numbers.\n");
        } else {
            sb.append("\n[STRICT ZERO PAGE NUMBERS RULE]: DO NOT include any page numbers, page badges, or page citations (e.g. NEVER write 'Page 1', '[Page 2]', '(Page 5)', or 'Reference: Page X') anywhere in your answer. The user will ask for page references explicitly in a follow-up message if they want them. Keep the entire response clean and focused strictly on explaining the topic without mentioning page numbers.\n");
        }

        return sb.toString().trim();
    }

    /**
     * Dynamically classifies the requested response depth based on explicit user cues.
     */
    public DepthLevel classifyDepthLevel(String query, UserIntent intent, LengthConstraint lengthConstraint) {
        if (lengthConstraint == LengthConstraint.ONE_LINER ||
            intent == UserIntent.FACTUAL_SHORT ||
            intent == UserIntent.LIST_EXTRACTION) {
            return DepthLevel.LEVEL_1_QUICK;
        }

        String lower = query != null ? query.toLowerCase().trim() : "";

        // Level 1: Quick / Short reply triggers
        if (lower.contains("short summary") || lower.contains("short reply") || lower.contains("in short") ||
            lower.contains("short me") || lower.contains("briefly") || lower.contains("quick") ||
            lower.contains("kewal naam") || lower.contains("sirf naam") || lower.contains("only name") ||
            lower.contains("just name") || lower.contains("1 line") || lower.contains("one line") ||
            lower.contains("single line") || lower.contains("2-3 line") || lower.contains("2-3 lines")) {
            return DepthLevel.LEVEL_1_QUICK;
        }

        // Level 4: Deep / Masterclass / "Or more" / Complex Scenarios / Tutorial triggers
        if (lower.contains("or more") || lower.contains("aur batao") || lower.contains("deeply explain") ||
            lower.contains("deep dive") || lower.contains("complete detail") || lower.contains("from basics") ||
            lower.contains("thoroughly explain") || lower.contains("step by step samjhao") ||
            lower.contains("pura samjhao") || lower.contains("interview preparation") ||
            lower.contains("interview ke hisaab se") || lower.contains("interview ke liye") ||
            lower.contains("masterclass") || lower.contains("complex scenario") ||
            lower.contains("complex scenarios") || lower.contains("production level") ||
            lower.contains("production ready") || intent == UserIntent.DEEP_EXPLANATION ||
            intent == UserIntent.INTERVIEW_PREPARATION) {
            return DepthLevel.LEVEL_4_DEEP;
        }

        // Level 3: Detailed explanation triggers ("detail mein", "achhe se", "explain with example")
        if (lower.contains("detail mein") || lower.contains("achhe se") || lower.contains("properly explain") ||
            lower.contains("in detail") || lower.contains("detailed explanation") ||
            lower.contains("explain with example") || lower.contains("with example") ||
            lower.contains("example ke sath") || intent == UserIntent.COMPARISON) {
            return DepthLevel.LEVEL_3_DETAILED;
        }

        // Level 2: Normal / Balanced ("more explain", "more explane", standard explanation)
        return DepthLevel.LEVEL_2_NORMAL;
    }

    public QuestionComplexity classifyComplexity(String query, UserIntent intent, LengthConstraint lengthConstraint) {
        if (lengthConstraint == LengthConstraint.ONE_LINER ||
            intent == UserIntent.FACTUAL_SHORT ||
            intent == UserIntent.LIST_EXTRACTION) {
            return QuestionComplexity.SIMPLE;
        }

        String lower = query != null ? query.toLowerCase().trim() : "";

        if (intent == UserIntent.COMPARISON ||
            intent == UserIntent.ANALYSIS_REASONING ||
            intent == UserIntent.DESIGN_PATTERNS ||
            intent == UserIntent.CODE_IMPLEMENTATION ||
            intent == UserIntent.CODE_DEBUG_FIX ||
            intent == UserIntent.DEEP_EXPLANATION ||
            intent == UserIntent.INTERVIEW_PREPARATION ||
            intent == UserIntent.ARCHITECTURE ||
            lengthConstraint == LengthConstraint.DETAILED_DEPTH ||
            lower.contains("compare") ||
            lower.contains("vs") ||
            lower.contains("difference") ||
            lower.contains("analyze") ||
            lower.contains("why") ||
            lower.contains("in detail") ||
            lower.contains("deep dive") ||
            lower.contains("architecture") ||
            lower.contains("complex")) {
            return QuestionComplexity.COMPLEX;
        }

        if (lower.startsWith("what is ") || lower.startsWith("who is ") ||
            lower.startsWith("define ") || lower.contains("port") ||
            lower.contains("in short") || lower.contains("briefly")) {
            if (lower.length() < 40) {
                return QuestionComplexity.SIMPLE;
            }
        }

        return QuestionComplexity.MODERATE;
    }

    public UserIntent classifyIntent(String query, boolean isDocumentContext) {
        String lower = query != null ? query.toLowerCase().trim() : "";

        // 1. Follow-up resolution
        if (lower.equals("why?") || lower.equals("why") || lower.startsWith("why so") ||
            lower.startsWith("explain this") || lower.startsWith("isko explain") ||
            lower.contains("second one") || lower.contains("dusra wala") ||
            lower.equals("give an example") || lower.equals("example do") ||
            lower.equals("compare them") || lower.equals("dono me antar") ||
            lower.equals("or more") || lower.equals("aur batao") ||
            lower.startsWith("same for ")) {
            return UserIntent.CONVERSATIONAL_FOLLOWUP;
        }

        // 2. Direct Short / Names-only / Factual
        if (lower.contains("kewal naam") || lower.contains("only name") || lower.contains("just the names") ||
            lower.contains("list only") || lower.contains("sirf naam")) {
            return UserIntent.LIST_EXTRACTION;
        }

        // 3. Port checks / direct commands
        if (lower.contains("port") && (lower.contains("check") || lower.contains("command") || lower.contains("kill") || lower.contains("findstr"))) {
            return UserIntent.FACTUAL_SHORT;
        }

        // 4. Summaries
        if (lower.contains("short summary") || lower.contains("summary") || lower.contains("summarize") ||
            lower.contains("nichod") || lower.contains("overview") || lower.startsWith("brief me")) {
            return UserIntent.SUMMARY;
        }

        // 5. Comparison
        if (lower.contains(" vs ") || lower.contains(" versus ") || lower.contains("difference between") ||
            lower.contains("compare ") || lower.contains("antar kya hai") || lower.contains("which is better") || lower.contains("kaun sa better")) {
            return UserIntent.COMPARISON;
        }

        // 6. Interview Preparation
        if (lower.contains("interview") || lower.contains("interview ke liye") || lower.contains("interview preparation") ||
            lower.contains("interview ke hisaab se") || lower.contains("interview questions")) {
            return UserIntent.INTERVIEW_PREPARATION;
        }

        // 7. Step-by-step How-To
        if (lower.startsWith("how to") || lower.startsWith("how do i") || lower.startsWith("kaise kare") ||
            lower.contains("step by step") || lower.contains("steps to") || lower.contains("kaise setup")) {
            return UserIntent.STEP_BY_STEP_HOWTO;
        }

        // 8. Design Patterns
        if (isDesignPatternQuery(lower)) {
            return UserIntent.DESIGN_PATTERNS;
        }

        // 9. Debug / Error fix / Troubleshooting
        if (lower.contains("error") || lower.contains("exception") || lower.contains("stacktrace") || lower.contains("fix ") ||
            lower.contains("crash") || lower.contains("kyu aa raha hai") || lower.contains("not working") || lower.contains("troubleshoot")) {
            return UserIntent.CODE_DEBUG_FIX;
        }

        // 10. Code Generation & Implementation
        if (lower.contains("write code") || lower.contains("create class") || lower.contains("code do") ||
            lower.contains("implement ") || lower.contains("spring boot code") || lower.contains("controller code") ||
            lower.contains("java code")) {
            return UserIntent.CODE_IMPLEMENTATION;
        }

        // 11. Deep Explanation / Tutorial / "Or more" / Complex Scenarios
        if (lower.contains("deeply explain") || lower.contains("deep dive") ||
            lower.contains("pura samjhao") || lower.contains("from basics") || lower.contains("thoroughly explain") ||
            lower.contains("step by step samjhao") || lower.contains("or more") || lower.contains("complex scenario") ||
            lower.contains("complex scenarios")) {
            return UserIntent.DEEP_EXPLANATION;
        }

        // 12. Definition
        if (lower.startsWith("what is ") || lower.startsWith("define ") || lower.startsWith("definition of")) {
            return UserIntent.DEFINITION;
        }

        // 13. Concept Explanation (what is X / more explain / kya hota hai)
        if (lower.contains("more explain") || lower.contains("more explane") ||
            lower.startsWith("kya hota hai") || lower.endsWith("kya hai") ||
            lower.startsWith("explain ") || lower.contains("samjhao")) {
            return UserIntent.CONCEPT_EXPLANATION;
        }

        if (isDocumentContext) {
            return UserIntent.DOCUMENT_GROUNDED_QA;
        }

        return UserIntent.GENERAL_QA;
    }

    public LengthConstraint detectLengthConstraint(String query, UserIntent intent) {
        String lower = query != null ? query.toLowerCase().trim() : "";

        if (extractExplicitLineConstraint(query) != null) {
            return LengthConstraint.EXPLICIT_FEW_LINES;
        }

        if (lower.contains("1 line") || lower.contains("one line") || lower.contains("single line") ||
            lower.contains("kewal naam") || lower.contains("only name") || lower.contains("just name")) {
            return LengthConstraint.ONE_LINER;
        }

        if (lower.contains("short answer") || lower.contains("in short") || lower.contains("short me") ||
            lower.contains("short reply") || lower.contains("short summary") || lower.contains("briefly") ||
            lower.contains("concise") || lower.contains("quick") ||
            intent == UserIntent.FACTUAL_SHORT || intent == UserIntent.LIST_EXTRACTION) {
            return LengthConstraint.SHORT_CONCISE;
        }

        if (lower.contains("in detail") || lower.contains("deep dive") || lower.contains("thoroughly") ||
            lower.contains("complete guide") || lower.contains("pura samjhao") || lower.contains("detailed explanation") ||
            lower.contains("or more") || lower.contains("deeply explain") || lower.contains("step by step samjhao")) {
            return LengthConstraint.DETAILED_DEPTH;
        }

        return LengthConstraint.BALANCED_STANDARD;
    }

    public String extractExplicitLineConstraint(String query) {
        if (query == null) return null;
        Matcher m = Pattern.compile("(?i)(\\d+(?:\\s*-\\s*\\d+)?\\s*(?:lines|line|sentences|points|bullets))").matcher(query);
        if (m.find()) {
            return m.group(1);
        }
        String lower = query.toLowerCase();
        if (lower.contains("4-5 line") || lower.contains("4-5 lines")) {
            return "4-5 lines";
        }
        if (lower.contains("2-3 line") || lower.contains("2-3 lines")) {
            return "2-3 lines";
        }
        return null;
    }

    private boolean isDesignPatternQuery(String lower) {
        List<String> patterns = List.of(
                "design pattern", "gof", "gang of four",
                "factory method", "abstract factory", "builder pattern", "prototype pattern", "singleton",
                "adapter pattern", "bridge pattern", "composite pattern", "decorator pattern", "facade pattern",
                "flyweight pattern", "proxy pattern",
                "chain of responsibility", "command pattern", "interpreter pattern", "iterator pattern",
                "mediator pattern", "memento pattern", "observer pattern", "state pattern", "strategy pattern",
                "template method", "visitor pattern", "creational pattern", "structural pattern", "behavioral pattern"
        );
        for (String p : patterns) {
            if (lower.contains(p)) return true;
        }
        return false;
    }

    public boolean detectHinglish(String query) {
        if (query == null) return false;
        String lower = query.toLowerCase();
        List<String> hindiWords = List.of(
                "kya", "kaise", "batao", "hai", "karo", "isme", "iska", "iske", "kyu", "kyun",
                "mai", "ye", "wo", "hoga", "hota", "kuch", "chahiye", "samjhao", "sahi", "nahi",
                "kare", "karte", "dikhaye", "dedo", "bhi", "wala", "wali", "wale", "mein", "ko",
                "par", "pe", "se", "aur", "toh", "kewal", "sirf", "itna", "har", "trah", "ke",
                "kre", "kare", "ek", "de", "or", "more"
        );
        for (String hw : hindiWords) {
            if (Pattern.compile("\\b" + Pattern.quote(hw) + "\\b").matcher(lower).find()) {
                return true;
            }
        }
        return false;
    }

    private boolean detectJavaOrSpringContext(String query) {
        if (query == null) return false;
        String lower = query.toLowerCase();
        List<String> javaTerms = List.of(
                "java", "spring", "spring boot", "bean", "controller", "singleton", "factory",
                "interface", "class", "jvm", "multithreading", "thread", "concurrency",
                "synchronized", "volatile", "hashmap", "concurrenthashmap", "hibernate", "jpa"
        );
        for (String term : javaTerms) {
            if (lower.contains(term)) return true;
        }
        return false;
    }
}

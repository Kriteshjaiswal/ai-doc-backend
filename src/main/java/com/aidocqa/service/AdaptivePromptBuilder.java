package com.aidocqa.service;

import com.aidocqa.entity.ChatHistory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Adaptive Prompt Architecture Engine.
 * Intelligently classifies user intent, detects language (Hinglish/English),
 * injects conversation memory, enforces zero-empty-headings rules,
 * and manages clean, accurate page references at the end of responses.
 */
@Component
public class AdaptivePromptBuilder {

    public enum UserIntent {
        DESIGN_PATTERNS,
        JAVA_SPRING_TECHNICAL,
        TROUBLESHOOTING_DEBUG,
        CODE_GENERATION,
        CODE_DEBUGGING_REFACTOR,
        COMPARISON,
        DIRECT_COMMAND_FACT,
        REWRITE_OR_PROMPT,
        DOCUMENT_GROUNDED_QA,
        GENERAL_CONVERSATIONAL
    }

    public enum ResponseDepth {
        LEVEL_1_DIRECT_COMMAND,
        LEVEL_2_CONCISE,
        LEVEL_3_STANDARD_LEARNING,
        LEVEL_4_DEEP_DIVE
    }

    /**
     * Builds an intent-optimized, context-aware prompt for Gemini AI.
     */
    public String buildAdaptivePrompt(
            String documentText,
            List<String> pageImagesBase64,
            String question,
            List<ChatHistory> recentHistory
    ) {
        String cleanQ = question != null ? question.trim() : "";
        String lowerQ = cleanQ.toLowerCase();

        boolean hasDocText = documentText != null && !documentText.isBlank();
        boolean hasImages = pageImagesBase64 != null && !pageImagesBase64.isEmpty();
        boolean isDocumentContext = hasDocText || hasImages;

        // 1. Detect Intent, Language & Depth
        UserIntent intent = classifyIntent(cleanQ, isDocumentContext);
        boolean isHinglish = detectHinglish(cleanQ);
        ResponseDepth depth = detectDepth(cleanQ, intent);

        StringBuilder prompt = new StringBuilder();

        // 2. System Role & Core Persona
        prompt.append("=== SYSTEM INSTRUCTION ===\n");
        prompt.append("You are DocuMind AI, a world-class AI research assistant, expert software architect, and technical mentor.\n");
        prompt.append("Your mission is to provide deeply insightful, comprehensive, easily readable, and meticulously accurate answers.\n\n");

        // 3. Ironclad Quality & Completeness Rules
        prompt.append("--- CRITICAL COMPLETENESS & FORMATTING RULES ---\n");
        prompt.append("1. NEVER LEAVE EMPTY HEADINGS OR LABELS:\n");
        prompt.append("   - NEVER produce empty headings like '1. Abstract Factory \\n Purpose:' with no text!\n");
        prompt.append("   - If you list items, patterns, or sections, EVERY SINGLE ITEM must contain full, rich, meaningful explanations immediately below it.\n");
        prompt.append("   - Example for Design Patterns:\n");
        prompt.append("     1. **Abstract Factory**\n");
        prompt.append("        - **Purpose:** Ek interconnected family of related objects ko create karne ke liye use hota hai bina unke concrete classes specify kiye.\n");
        prompt.append("     2. **Builder**\n");
        prompt.append("        - **Purpose:** Complex objects ko step-by-step construct karta hai, especially jab multiple optional parameters ho.\n\n");

        prompt.append("2. EXACT PDF VIEWER PAGE CITATIONS (OFFSET RESOLUTION & 100% NAVIGATION ACCURACY):\n");
        prompt.append("   - Many books and documents have front-matter (preface, TOC), so Book Page Y is physically on PDF Document Page X (e.g. Book Page 87 is on PDF Page 107).\n");
        prompt.append("   - Each section in context is labeled with: '--- PDF PAGE X (Book Page Y) ---' or '--- PDF PAGE X ---'.\n");
        prompt.append("   - When generating citation links/badges, ALWAYS use the 'PDF PAGE X' number (e.g. [Page 107] or [Page 107] (Book Page 87)).\n");
        prompt.append("   - NEVER cite only the Book Page number (e.g. NEVER write [Page 87] for Abstract Factory when it is on PDF Page 107), because clicking [Page 87] in the Document Viewer will open PDF page 87 (which is 20 pages before Abstract Factory!).\n");
        prompt.append("   - Format references cleanly at the end of the response:\n");
        prompt.append("     ### 📚 Document References\n");
        prompt.append("     - **PDF Page 107 (Book Page 87):** Abstract Factory Pattern Details & Intent\n");
        prompt.append("     - **PDF Page 117 (Book Page 97):** Builder Pattern Intent & Structure\n");
        prompt.append("     - **PDF Page 127 (Book Page 107):** Factory Method Pattern Specifications\n\n");

        prompt.append("3. MANDATORY CODE BLOCKS WITH LANGUAGE BADGES:\n");
        prompt.append("   - Whenever you provide or quote ANY code snippet, function, class, or terminal command (Python, Java, TypeScript, SQL, Bash, YAML, HTML, etc.), you MUST ALWAYS enclose it in triple-backtick code blocks with the language tag (e.g. ```python\\n...\\n``` or ```java\\n...\\n``` or ```cmd\\n...\\n```).\n");
        prompt.append("   - NEVER output raw unformatted code lines outside of code blocks.\n\n");

        // 4. Language & Tone Guidelines
        prompt.append("--- LANGUAGE & COMMUNICATION STYLE ---\n");
        if (isHinglish) {
            prompt.append("LANGUAGE: Natural, fluent Hinglish (Senior Indian Software Engineer tone).\n");
            prompt.append("- Use natural Hinglish sentences around English technical concepts.\n");
            prompt.append("- NEVER translate technical terms unnaturally (Keep terms like: API, Server, Database, Class, Object, Interface, Method, Exception, Spring Boot, Port, Controller, Bean, Repository, Thread, Docker, Deployment in English).\n");
            prompt.append("- Example: 'Factory Method ka main purpose object creation ko encapsulate karna hai, taaki client ko concrete class ka direct dependency na ho.'\n");
        } else {
            prompt.append("LANGUAGE: Clear, professional, concise English.\n");
        }
        prompt.append("- Avoid generic conversational filler (DO NOT say 'In today's fast paced world...', 'Let's dive deep...', 'As we all know...'). Start directly with the answer.\n\n");

        // 5. Multi-Turn Conversation Memory
        if (recentHistory != null && !recentHistory.isEmpty()) {
            prompt.append("--- RECENT CONVERSATION HISTORY (FOR CONTEXT & PRONOUN RESOLUTION) ---\n");
            List<ChatHistory> chronological = new ArrayList<>(recentHistory);
            Collections.reverse(chronological);

            int historyCount = Math.min(chronological.size(), 4);
            for (int i = chronological.size() - historyCount; i < chronological.size(); i++) {
                ChatHistory h = chronological.get(i);
                prompt.append("User: ").append(h.getQuestion()).append("\n");
                String shortAns = h.getAnswer();
                if (shortAns != null && shortAns.length() > 300) {
                    shortAns = shortAns.substring(0, 300) + "...";
                }
                prompt.append("AI: ").append(shortAns).append("\n---\n");
            }
            prompt.append("NOTE: If the user uses follow-up pronouns like 'iska', 'isme', 'why?', 'how to fix it?', resolve them using the conversation history above.\n\n");
        }

        // 6. Document Context (RAG) & Domain Adaptivity
        if (isDocumentContext) {
            prompt.append("--- GROUNDED DOCUMENT CONTEXT ---\n");
            prompt.append(hasDocText ? documentText : "PDF pages attached as rendered images.\n");
            prompt.append("\nDOCUMENT TYPE & DOMAIN ADAPTIVITY:\n");
            prompt.append("- Technical Books / Coding: Explain patterns, architectures, schemas, and code implementations with complete explanations for each item.\n");
            prompt.append("- Literature / Novels / Drama: Analyze narrative themes, character motivations, dramatic conflicts, and dialogue context.\n");
            prompt.append("- Legal Contracts: Dissect obligations, liabilities, indemnity, termination terms, and governing law.\n");
            prompt.append("- Financials: Break down revenues, costs, margins, EBITDA, and balance sheet items.\n");
            prompt.append("- If information is missing from the document, state clearly that it is not covered in the provided text.\n\n");
        }

        // 7. Intent-Specific Strategy Instructions
        prompt.append("--- INTENT-DRIVEN RESPONSE STRATEGY: ").append(intent.name()).append(" ---\n");
        appendIntentInstructions(prompt, intent, depth);

        // 8. User Input
        prompt.append("\n=== USER INPUT ===\n");
        prompt.append(cleanQ).append("\n");

        return prompt.toString();
    }

    private UserIntent classifyIntent(String query, boolean isDocumentContext) {
        String lower = query.toLowerCase();

        // 1. Direct command or simple port check
        if (lower.contains("port") && (lower.contains("check") || lower.contains("command") || lower.contains("kill") || lower.contains("findstr") || lower.contains("lsof"))) {
            return UserIntent.DIRECT_COMMAND_FACT;
        }
        if (lower.startsWith("command to") || lower.startsWith("git command") || lower.startsWith("docker command") || lower.endsWith("command batao") || lower.endsWith("command do")) {
            return UserIntent.DIRECT_COMMAND_FACT;
        }

        // 2. Troubleshooting & Error Debugging
        if (lower.contains("error") || lower.contains("exception") || lower.contains("failed") || lower.contains("stacktrace") ||
            lower.contains("kyu aa raha hai") || lower.contains("fix karo") || lower.contains("not working") || lower.contains("crash") ||
            lower.contains("conflict") || lower.contains("nullpointer") || lower.contains("500 internal")) {
            return UserIntent.TROUBLESHOOTING_DEBUG;
        }

        // 3. Comparison
        if (lower.contains(" vs ") || lower.contains(" versus ") || lower.contains("difference between") ||
            lower.contains("compare ") || lower.contains("antar kya hai") || lower.contains("difference kya hai") ||
            lower.contains("which is better") || lower.contains("kaun sa better hai")) {
            return UserIntent.COMPARISON;
        }

        // 4. Design Patterns (GoF 23 + Architectural Patterns)
        if (isDesignPatternQuery(lower)) {
            return UserIntent.DESIGN_PATTERNS;
        }

        // 5. Code Debugging / Refactoring
        if (lower.contains("debug this") || lower.contains("refactor") || lower.contains("isme validation") ||
            lower.contains("optimize this code") || lower.contains("find bug") || lower.contains("code improve")) {
            return UserIntent.CODE_DEBUGGING_REFACTOR;
        }

        // 6. Code Generation
        if (lower.contains("write code") || lower.contains("create class") || lower.contains("implement ") ||
            lower.contains("code do") || lower.contains("java code") || lower.contains("spring boot code") ||
            lower.contains("controller code") || lower.contains("api code")) {
            return UserIntent.CODE_GENERATION;
        }

        // 7. Rewrite or Prompt Generation
        if (lower.contains("rewrite") || lower.contains("professional bana do") || lower.contains("give prompt") ||
            lower.contains("prompt generate") || lower.contains("email format")) {
            return UserIntent.REWRITE_OR_PROMPT;
        }

        // 8. Java / Spring / OOP Technical learning
        if (lower.contains("java") || lower.contains("spring") || lower.contains("oop") || lower.contains("interface") ||
            lower.contains("multithreading") || lower.contains("hibernate") || lower.contains("jpa") || lower.contains("dsa") ||
            lower.contains("microservice") || lower.contains("rest api")) {
            return UserIntent.JAVA_SPRING_TECHNICAL;
        }

        // 9. Document Grounded QA
        if (isDocumentContext) {
            return UserIntent.DOCUMENT_GROUNDED_QA;
        }

        return UserIntent.GENERAL_CONVERSATIONAL;
    }

    private boolean isDesignPatternQuery(String lower) {
        List<String> patterns = List.of(
                "design pattern", "gof", "gang of four",
                "factory method", "abstract factory", "builder pattern", "prototype pattern", "singleton",
                "adapter pattern", "bridge pattern", "composite pattern", "decorator pattern", "facade pattern",
                "flyweight pattern", "proxy pattern",
                "chain of responsibility", "command pattern", "interpreter pattern", "iterator pattern",
                "mediator pattern", "memento pattern", "observer pattern", "state pattern", "strategy pattern",
                "template method", "visitor pattern", "creational pattern", "structural pattern", "behavioral pattern",
                "creational design pattern", "structural design pattern", "behavioral design pattern"
        );
        for (String p : patterns) {
            if (lower.contains(p)) return true;
        }
        return false;
    }

    private boolean detectHinglish(String query) {
        String lower = query.toLowerCase();
        List<String> hindiWords = List.of(
                "kya", "kaise", "batao", "hai", "karo", "isme", "iska", "iske", "kyu", "kyun",
                "mai", "ye", "wo", "hoga", "hota", "kuch", "chahiye", "samjhao", "sahi", "nahi",
                "kare", "karte", "dikhaye", "dedo", "bhi", "wala", "wali", "wale", "mein", "ko",
                "par", "pe", "se", "aur", "toh", "glt", "galat"
        );
        for (String hw : hindiWords) {
            if (Pattern.compile("\\b" + Pattern.quote(hw) + "\\b").matcher(lower).find()) {
                return true;
            }
        }
        return false;
    }

    private ResponseDepth detectDepth(String query, UserIntent intent) {
        String lower = query.toLowerCase();
        if (intent == UserIntent.DIRECT_COMMAND_FACT || lower.contains("short me") || lower.contains("in short") || lower.contains("1 line")) {
            return ResponseDepth.LEVEL_1_DIRECT_COMMAND;
        }
        if (lower.contains("deeply") || lower.contains("in depth") || lower.contains("complete guide") || lower.contains("interview") || intent == UserIntent.DESIGN_PATTERNS) {
            return ResponseDepth.LEVEL_4_DEEP_DIVE;
        }
        if (lower.contains("briefly") || lower.contains("concise") || lower.contains("quick overview")) {
            return ResponseDepth.LEVEL_2_CONCISE;
        }
        return ResponseDepth.LEVEL_3_STANDARD_LEARNING;
    }

    private void appendIntentInstructions(StringBuilder prompt, UserIntent intent, ResponseDepth depth) {
        switch (intent) {
            case DIRECT_COMMAND_FACT -> {
                prompt.append("TASK FORMAT: DIRECT COMMAND / FACT FIRST\n");
                prompt.append("1. Provide the exact command or direct factual answer FIRST in a code block.\n");
                prompt.append("2. Add a short 1-2 sentence explanation of what each flag or parameter does.\n");
                prompt.append("3. DO NOT write long essays or unwanted history.\n");
            }
            case TROUBLESHOOTING_DEBUG -> {
                prompt.append("TASK FORMAT: ACTIONABLE DEBUGGING (Problem -> Root Cause -> Fix -> Verification)\n");
                prompt.append("1. ### What's Happening: 1 clear sentence identifying the exact issue.\n");
                prompt.append("2. ### Root Cause: Technical explanation of why it occurred.\n");
                prompt.append("3. ### Exact Fix: Clear runnable code or exact terminal command to resolve it.\n");
                prompt.append("4. ### Verification: Exact step to confirm the issue is permanently resolved.\n");
            }
            case COMPARISON -> {
                prompt.append("TASK FORMAT: STRUCTURED COMPARISON TABLE + VERDICT\n");
                prompt.append("1. Provide a clean Markdown comparison table with columns: `| Feature / Aspect | Option A | Option B |`.\n");
                prompt.append("2. ### Key Architectural Difference: 2-3 high-impact differentiator points.\n");
                prompt.append("3. ### Practical Recommendation: When to choose Option A vs Option B in real projects.\n");
            }
            case DESIGN_PATTERNS -> {
                prompt.append("TASK FORMAT: 10/10 EXPERT MENTOR DESIGN PATTERN GUIDE\n");
                prompt.append("CRITICAL: When listing patterns (e.g. Creational Patterns):\n");
                prompt.append("- For EVERY SINGLE pattern listed, provide its full **Category**, **Intent**, and **Purpose** immediately! NEVER leave any pattern with an empty 'Purpose:'.\n");
                prompt.append("- Structure:\n");
                prompt.append("  1. **Pattern Breakdown:** List all patterns with complete 2-3 line Purpose and real-world intuition.\n");
                prompt.append("  2. **Problem Solved:** Explain what bad/tightly-coupled code fails without this pattern.\n");
                prompt.append("  3. **Clean Runnable Java Code Example:** Modern, clean, well-named Java snippet.\n");
                prompt.append("  4. **When to Use & When NOT to Use:** Practical enterprise use cases and trade-offs.\n");
                prompt.append("  5. **Interview One-Liner / Memory Trick:** 1 memorable sentence for tech interviews.\n");
                prompt.append("  6. If document context is provided, list verified page citations at the bottom under '### 📚 Document References'.\n");
            }
            case CODE_GENERATION -> {
                prompt.append("TASK FORMAT: CLEAN RUNNABLE CODE IMPLEMENTATION\n");
                prompt.append("1. ### Approach: 1-2 sentence summary of implementation strategy.\n");
                prompt.append("2. Clean, production-ready code with appropriate language annotations and best practices.\n");
                prompt.append("3. ### Key Method Explanation: Highlight 2-3 critical logic points.\n");
            }
            case CODE_DEBUGGING_REFACTOR -> {
                prompt.append("TASK FORMAT: FOCUSED CODE REFACTOR & FIX\n");
                prompt.append("1. Identify the bug or inefficiency directly.\n");
                prompt.append("2. Show the corrected, refactored code.\n");
                prompt.append("3. Explain only what changed and why it is safer/faster.\n");
            }
            case REWRITE_OR_PROMPT -> {
                prompt.append("TASK FORMAT: COPY-PASTE READY OUTPUT\n");
                prompt.append("1. Output the finished, rewritten content or copy-paste prompt directly.\n");
                prompt.append("2. Do NOT write unnecessary meta-theory explaining why you wrote it.\n");
            }
            case JAVA_SPRING_TECHNICAL -> {
                prompt.append("TASK FORMAT: SENIOR DEVELOPER TECHNICAL EXPLANATION\n");
                prompt.append("1. Clear, intuitive definition in 2-3 sentences.\n");
                prompt.append("2. Why we need it in real applications.\n");
                prompt.append("3. Clean Java / Spring Boot code snippet.\n");
                prompt.append("4. Practical enterprise use case and common gotchas.\n");
            }
            case DOCUMENT_GROUNDED_QA -> {
                prompt.append("TASK FORMAT: 100% GROUNDED DOCUMENT ANALYSIS\n");
                prompt.append("1. Direct, clear, comprehensive answer explaining the findings in full detail.\n");
                prompt.append("2. If listing multiple points/sections, give full explanations for each point (NO empty labels).\n");
                prompt.append("3. If comparing clauses/metrics, use a structured Markdown table.\n");
                prompt.append("4. At the very end of the response, provide:\n");
                prompt.append("   ### 📚 Document References\n");
                prompt.append("   - List only verified page numbers explicitly found in the document context.\n");
            }
            default -> {
                prompt.append("TASK FORMAT: HIGH-READABILITY STRUCTURED EXPLANATION\n");
                prompt.append("1. Direct, intuitive explanation with bold key terms.\n");
                prompt.append("2. Structured bullet points and examples where helpful.\n");
            }
        }
    }
}

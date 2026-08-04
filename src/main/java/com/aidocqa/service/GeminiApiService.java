package com.aidocqa.service;

import com.aidocqa.exception.GeminiApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class GeminiApiService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${groq.api.key:}")
    private String groqApiKey;

    @Value("${groq.model:llama-3.3-70b-versatile}")
    private String groqModel;

    @Value("${groq.api.url:https://api.groq.com/openai/v1/chat/completions}")
    private String groqApiUrl;

    @Value("${gemini.api.key:}")
    private String geminiApiKey;

    @Value("${gemini.api.url:https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent}")
    private String geminiApiUrl;

    private static final List<String> GROQ_FALLBACK_MODELS = List.of(
            "llama-3.3-70b-versatile",
            "llama-3.1-8b-instant",
            "llama3-70b-8192",
            "mixtral-8x7b-32768",
            "gemma2-9b-it"
    );

    private static final List<String> GEMINI_FALLBACK_MODELS = List.of(
            "gemini-1.5-flash",
            "gemini-2.0-flash",
            "gemini-1.5-pro"
    );

    /**
     * Sends the document text and user question to the AI Service (Groq / Gemini) with auto-fallback to alternative models
     * and local document text extraction if external API fails.
     *
     * @param documentText the extracted text from the PDF document
     * @param question     the user's question
     * @return the AI-generated answer or local extraction answer
     */
    public String generateAnswer(String documentText, String question) {
        log.info("Processing question: {}", question);

        // 1. Try Groq AI API if configured
        if (groqApiKey != null && !groqApiKey.isBlank()) {
            log.info("Attempting to generate answer using Groq AI API (model: {})...", groqModel);
            try {
                return executeGroqCall(groqModel, documentText, question);
            } catch (Exception e) {
                log.warn("Primary Groq model ({}) call failed: {}. Attempting Groq fallback models...", groqModel, e.getMessage());
            }

            for (String fallbackModel : GROQ_FALLBACK_MODELS) {
                if (fallbackModel.equalsIgnoreCase(groqModel)) continue;
                try {
                    log.info("Attempting Groq model fallback: {}", fallbackModel);
                    return executeGroqCall(fallbackModel, documentText, question);
                } catch (Exception e) {
                    log.warn("Groq fallback model {} failed: {}", fallbackModel, e.getMessage());
                }
            }
        }

        // 2. Try Gemini API as secondary provider if available
        if (geminiApiKey != null && !geminiApiKey.isBlank()) {
            log.info("Attempting to generate answer using Gemini API...");
            try {
                return executeGeminiCall(geminiApiUrl, documentText, question);
            } catch (Exception e) {
                log.warn("Primary Gemini API call failed: {}. Attempting Gemini fallback models...", e.getMessage());
            }

            String baseUrl = "https://generativelanguage.googleapis.com/v1beta/models/";
            for (String model : GEMINI_FALLBACK_MODELS) {
                String fallbackUrl = baseUrl + model + ":generateContent";
                try {
                    log.info("Attempting Gemini model fallback: {}", model);
                    return executeGeminiCall(fallbackUrl, documentText, question);
                } catch (Exception e) {
                    log.warn("Gemini fallback model {} failed: {}", model, e.getMessage());
                }
            }
        }

        // 3. Fallback to Local Document Extractive Analysis if external APIs are unavailable
        log.warn("All external AI APIs unavailable or failed. Using local document Q&A engine.");
        return extractAnswerFromDocument(documentText, question);
    }

    private String executeGroqCall(String modelName, String documentText, String question) {
        String prompt = buildPrompt(documentText, question);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(groqApiKey);

        Map<String, Object> requestBody = Map.of(
                "model", modelName,
                "messages", List.of(
                        Map.of("role", "user", "content", prompt)
                )
        );

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        ResponseEntity<String> response = restTemplate.exchange(
                groqApiUrl,
                HttpMethod.POST,
                entity,
                String.class
        );

        return parseGroqResponse(response.getBody());
    }

    private String executeGeminiCall(String targetUrl, String documentText, String question) {
        String prompt = buildPrompt(documentText, question);
        Map<String, Object> requestBody = Map.of(
                "contents", List.of(
                        Map.of("parts", List.of(
                                Map.of("text", prompt)
                        ))
                )
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        String url = targetUrl.contains("?") ? targetUrl + "&key=" + geminiApiKey : targetUrl + "?key=" + geminiApiKey;

        ResponseEntity<String> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                entity,
                String.class
        );

        return parseGeminiResponse(response.getBody());
    }

    private String parseGroqResponse(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode choices = root.path("choices");

            if (choices.isArray() && !choices.isEmpty()) {
                JsonNode message = choices.get(0).path("message");
                String content = message.path("content").asText();
                if (content != null && !content.isBlank()) {
                    return content;
                }
            }

            log.warn("Unexpected Groq API response structure: {}", responseBody);
            throw new GeminiApiException("Unable to parse Groq AI response. Format unrecognized.");
        } catch (GeminiApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to parse Groq API response: {}", e.getMessage(), e);
            throw new GeminiApiException("Failed to parse Groq AI response", e);
        }
    }

    private String parseGeminiResponse(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode candidates = root.path("candidates");

            if (candidates.isArray() && !candidates.isEmpty()) {
                JsonNode content = candidates.get(0).path("content");
                JsonNode parts = content.path("parts");

                if (parts.isArray() && !parts.isEmpty()) {
                    return parts.get(0).path("text").asText();
                }
            }

            log.warn("Unexpected Gemini API response structure: {}", responseBody);
            throw new GeminiApiException("Unable to parse Gemini AI response. Format unrecognized.");
        } catch (GeminiApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to parse Gemini API response: {}", e.getMessage(), e);
            throw new GeminiApiException("Failed to parse Gemini AI response", e);
        }
    }

    private String extractAnswerFromDocument(String documentText, String question) {
        if (documentText == null || documentText.isBlank()) {
            return "No document text available to answer the question.";
        }

        Set<String> stopWords = Set.of("what", "is", "the", "a", "an", "this", "about", "in", "on", "for", "where", "how", "who", "when", "why", "to", "of", "and", "or", "can", "you", "tell", "me");
        String[] keywords = question.toLowerCase().replaceAll("[^a-zA-Z0-9 ]", "").split("\\s+");
        List<String> meaningfulKeywords = Arrays.stream(keywords)
                .filter(w -> w.length() > 2 && !stopWords.contains(w))
                .toList();

        String[] sentences = documentText.split("(?<=[.!?\\n])\\s+");
        List<Map.Entry<String, Integer>> scoredSentences = new ArrayList<>();

        for (String sentence : sentences) {
            String cleanSentence = sentence.trim();
            if (cleanSentence.isBlank()) continue;
            String lower = cleanSentence.toLowerCase();
            int score = 0;
            for (String kw : meaningfulKeywords) {
                if (lower.contains(kw)) {
                    score++;
                }
            }
            if (score > 0) {
                scoredSentences.add(Map.entry(cleanSentence, score));
            }
        }

        scoredSentences.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));

        if (!scoredSentences.isEmpty()) {
            StringBuilder sb = new StringBuilder("[Document QA Answer]:\n");
            int maxResults = Math.min(3, scoredSentences.size());
            for (int i = 0; i < maxResults; i++) {
                sb.append("- ").append(scoredSentences.get(i).getKey()).append("\n");
            }
            return sb.toString();
        }

        String preview = documentText.length() > 300 ? documentText.substring(0, 300) + "..." : documentText;
        return "[Document Overview]: " + preview.replaceAll("\\s+", " ").trim();
    }

    private String buildPrompt(String documentText, String question) {
        return """
                You are an intelligent document assistant. Based on the following document content, \
                answer the user's question accurately and concisely. If the answer is not found in the \
                document, clearly state that the information is not available in the provided document.

                --- DOCUMENT CONTENT ---
                %s
                --- END OF DOCUMENT ---

                USER QUESTION: %s

                Please provide a clear, concise, and accurate answer:
                """.formatted(documentText, question);
    }
}

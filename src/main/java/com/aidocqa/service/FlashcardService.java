package com.aidocqa.service;

import com.aidocqa.dto.FlashcardResponseDto;
import com.aidocqa.dto.FlashcardStatusUpdateRequestDto;
import com.aidocqa.entity.Document;
import com.aidocqa.entity.Flashcard;
import com.aidocqa.entity.User;
import com.aidocqa.exception.ResourceNotFoundException;
import com.aidocqa.repository.DocumentRepository;
import com.aidocqa.repository.FlashcardRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class FlashcardService {

    private final FlashcardRepository flashcardRepository;
    private final DocumentRepository documentRepository;
    private final GeminiApiService geminiApiService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional(readOnly = true)
    public List<FlashcardResponseDto> getFlashcards(User user, Long documentId) {
        List<Flashcard> cards;
        if (documentId != null) {
            cards = flashcardRepository.findByDocumentIdAndUserOrderByCreatedAtDesc(documentId, user);
        } else {
            cards = flashcardRepository.findByUserOrderByCreatedAtDesc(user);
        }
        return cards.stream().map(this::mapToResponseDto).toList();
    }

    @Transactional
    public List<FlashcardResponseDto> generateFlashcards(Long documentId, int count, User user) {
        Document document = documentRepository.findByIdAndUserId(documentId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Document not found with ID: " + documentId));

        String extractedText = document.getExtractedText();
        if (extractedText == null || extractedText.isBlank()) {
            throw new IllegalArgumentException("Selected document has no extracted text content.");
        }

        int targetCount = Math.max(3, Math.min(count, 20));
        List<Map<String, String>> rawCards = generateRawFlashcardsWithAI(extractedText, targetCount);

        List<Flashcard> createdFlashcards = new ArrayList<>();
        for (Map<String, String> cardData : rawCards) {
            String question = cardData.getOrDefault("question", "What is the key takeaway of this section?");
            String answer = cardData.getOrDefault("answer", "Document section key detail.");
            String difficulty = cardData.getOrDefault("difficulty", "Medium");

            Flashcard flashcard = Flashcard.builder()
                    .user(user)
                    .document(document)
                    .question(question)
                    .answer(answer)
                    .difficulty(normalizeDifficulty(difficulty))
                    .status("unseen")
                    .isFavorite(false)
                    .build();

            createdFlashcards.add(flashcardRepository.save(flashcard));
        }

        log.info("Successfully generated and saved {} flashcards for document ID: {}", createdFlashcards.size(), documentId);
        return createdFlashcards.stream().map(this::mapToResponseDto).toList();
    }

    @Transactional
    public FlashcardResponseDto updateFlashcardStatus(Long id, FlashcardStatusUpdateRequestDto dto, User user) {
        Flashcard flashcard = flashcardRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Flashcard not found with ID: " + id));

        if (!flashcard.getUser().getId().equals(user.getId())) {
            throw new ResourceNotFoundException("Flashcard not found with ID: " + id);
        }

        if (dto.getStatus() != null && !dto.getStatus().isBlank()) {
            flashcard.setStatus(dto.getStatus());
        }
        if (dto.getIsFavorite() != null) {
            flashcard.setFavorite(dto.getIsFavorite());
        }

        Flashcard updated = flashcardRepository.save(flashcard);
        return mapToResponseDto(updated);
    }

    @Transactional
    public void deleteFlashcard(Long id, User user) {
        Flashcard flashcard = flashcardRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Flashcard not found with ID: " + id));

        if (!flashcard.getUser().getId().equals(user.getId())) {
            throw new ResourceNotFoundException("Flashcard not found with ID: " + id);
        }

        flashcardRepository.delete(flashcard);
    }

    private List<Map<String, String>> generateRawFlashcardsWithAI(String text, int count) {
        String prompt = """
                Extract %d high-quality educational flashcard question-and-answer pairs covering the ENTIRE document from start to finish.
                Ensure coverage spans early foundational concepts, main chapters, and concluding takeaways across the whole document.
                Return ONLY a raw JSON array of objects with keys: "question", "answer", and "difficulty" (value MUST be "Easy", "Medium", or "Hard").
                Do NOT include markdown formatting or backticks like ```json ... ```. Output raw JSON array directly:
                [
                  {"question": "...", "answer": "...", "difficulty": "Medium"}
                ]
                """.formatted(count);

        try {
            com.aidocqa.dto.GeminiResponseDto aiDto = geminiApiService.generateAnswer(text, prompt);
            String rawResponse = aiDto != null ? aiDto.getAnswer() : null;
            String jsonArrayStr = extractJsonArrayString(rawResponse);

            if (jsonArrayStr != null && !jsonArrayStr.isBlank()) {
                List<Map<String, String>> parsed = objectMapper.readValue(jsonArrayStr, new TypeReference<>() {});
                if (!parsed.isEmpty()) {
                    return parsed;
                }
            }
        } catch (Exception e) {
            log.warn("AI JSON Flashcard generation failed or unparseable: {}. Falling back to extractive sentence analyzer.", e.getMessage());
        }

        // Fallback rule-based sentence extractive generator
        return fallbackExtractiveFlashcards(text, count);
    }

    private String extractJsonArrayString(String rawResponse) {
        if (rawResponse == null) return null;
        String cleaned = rawResponse.trim();

        // Remove markdown backticks if present
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.replaceAll("^```[a-zA-Z]*", "").replaceAll("```$", "").trim();
        }

        int start = cleaned.indexOf('[');
        int end = cleaned.lastIndexOf(']');
        if (start != -1 && end != -1 && end > start) {
            return cleaned.substring(start, end + 1);
        }
        return null;
    }

    private List<Map<String, String>> fallbackExtractiveFlashcards(String text, int count) {
        List<Map<String, String>> cards = new ArrayList<>();
        String[] sentences = text.split("(?<=[.!?\\n])\\s+");
        List<String> cleanSentences = Arrays.stream(sentences)
                .map(String::trim)
                .filter(s -> s.length() > 25)
                .toList();

        int step = Math.max(1, cleanSentences.size() / count);
        String[] difficulties = {"Easy", "Medium", "Hard"};

        for (int i = 0; i < cleanSentences.size() && cards.size() < count; i += step) {
            String sentence = cleanSentences.get(i);
            String question;
            String answer = sentence;

            if (sentence.toLowerCase().contains(" is ")) {
                String[] parts = sentence.split("(?i)\\b is \\b", 2);
                question = "What is " + parts[0].trim() + "?";
            } else if (sentence.toLowerCase().contains(" means ")) {
                String[] parts = sentence.split("(?i)\\b means \\b", 2);
                question = "What does " + parts[0].trim() + " mean?";
            } else {
                question = "What key concept is discussed in this sentence: \"" + shorten(sentence, 40) + "\"?";
            }

            String diff = difficulties[cards.size() % difficulties.length];
            cards.add(Map.of(
                    "question", question,
                    "answer", answer,
                    "difficulty", diff
            ));
        }

        if (cards.isEmpty()) {
            cards.add(Map.of(
                    "question", "What is the summary of this document?",
                    "answer", shorten(text, 200),
                    "difficulty", "Medium"
            ));
        }
        return cards;
    }

    private String shorten(String text, int maxLen) {
        if (text == null) return "";
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...";
    }

    private String normalizeDifficulty(String diff) {
        if (diff == null) return "Medium";
        String d = diff.trim();
        if (d.equalsIgnoreCase("Easy")) return "Easy";
        if (d.equalsIgnoreCase("Hard")) return "Hard";
        return "Medium";
    }

    private FlashcardResponseDto mapToResponseDto(Flashcard flashcard) {
        return FlashcardResponseDto.builder()
                .id(flashcard.getId())
                .documentId(flashcard.getDocument().getId())
                .docTitle(flashcard.getDocument().getFileName())
                .question(flashcard.getQuestion())
                .answer(flashcard.getAnswer())
                .difficulty(flashcard.getDifficulty())
                .status(flashcard.getStatus())
                .isFavorite(flashcard.isFavorite())
                .createdAt(flashcard.getCreatedAt())
                .build();
    }
}

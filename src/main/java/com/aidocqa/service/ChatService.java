package com.aidocqa.service;

import com.aidocqa.dto.ChatRequestDto;
import com.aidocqa.dto.ChatResponseDto;
import com.aidocqa.dto.GeminiResponseDto;
import com.aidocqa.entity.ChatHistory;
import com.aidocqa.entity.Document;
import com.aidocqa.entity.User;
import com.aidocqa.exception.ResourceNotFoundException;
import com.aidocqa.repository.ChatHistoryRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatHistoryRepository chatHistoryRepository;
    private final DocumentService documentService;
    private final GeminiApiService geminiApiService;
    private final PdfExtractorService pdfExtractorService;

    /**
     * Processes a user question against an optional document or general knowledge,
     * returning the AI answer.
     * Persists to chat history ONLY if the AI response execution was successful.
     *
     * @param request the chat request containing documentId and question
     * @param user    the authenticated user
     * @return the chat response DTO with the answer
     */
    public ChatResponseDto askQuestion(ChatRequestDto request, User user) {
        Document document = null;
        String contextText = "";

        List<String> pageImagesBase64 = null;

        StringBuilder contextBuilder = new StringBuilder();

        if (request.getDocumentId() != null) {
            try {
                document = documentService.getDocumentById(request.getDocumentId(), user);
                if (document != null) {
                    contextBuilder.append("=== DOCUMENT METADATA ===\n");
                    contextBuilder.append("Document Name: ").append(document.getFileName()).append("\n");
                    contextBuilder.append("Total Pages: ").append(document.getPageCount() != null ? document.getPageCount() : 1).append("\n");
                    if (document.getSummary() != null && !document.getSummary().isBlank()) {
                        contextBuilder.append("Executive Overview: ").append(document.getSummary()).append("\n");
                    }
                    contextBuilder.append("=========================\n\n");

                    String extracted = document.getExtractedText();
                    if (extracted != null && !extracted.isBlank()) {
                        contextBuilder.append(extracted);
                    }
                }
                if (document != null && document.getFilePath() != null) {
                    File pdfFile = new File(document.getFilePath());
                    if (pdfFile.exists()) {
                        if (contextBuilder.length() < 50) {
                            String structured = pdfExtractorService.extractStructuredDocContext(pdfFile);
                            contextBuilder.append(structured != null ? structured : "");
                        }
                        // If extracted text is short (< 150 chars), render page images for vision OCR analysis
                        if (contextBuilder.length() < 150) {
                            pageImagesBase64 = pdfExtractorService.renderPdfPagesAsBase64(pdfFile, 5);
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Could not retrieve text context for document ID {}: {}", request.getDocumentId(),
                        e.getMessage());
            }
        }

        contextText = contextBuilder.toString();

        log.info("Processing question: '{}' (documentId: {}) by user: {}", request.getQuestion(),
                request.getDocumentId(), user.getEmail());

        // Call Gemini AI Multimodal API with extracted text and rendered page images
        GeminiResponseDto aiResult = geminiApiService.generateAnswerMultimodal(contextText, pageImagesBase64,
                request.getQuestion());

        log.info("Grounding validation: provider={}, model={}, success={}, grounded={}",
                aiResult.getProvider(), aiResult.getModel(), aiResult.isSuccess(), aiResult.isGrounded());

        // Save chat history ONLY when success == true and answer is non-empty
        if (aiResult.isSuccess() && aiResult.getAnswer() != null && !aiResult.getAnswer().isBlank()) {
            ChatHistory chatHistory = ChatHistory.builder()
                    .document(document)
                    .user(user)
                    .question(request.getQuestion())
                    .answer(aiResult.getAnswer())
                    .build();

            ChatHistory savedChat = chatHistoryRepository.save(chatHistory);
            log.info("Saving chat history... Success with ID: {}", savedChat.getId());

            return ChatResponseDto.builder()
                    .id(savedChat.getId())
                    .documentId(document != null ? document.getId() : null)
                    .question(savedChat.getQuestion())
                    .answer(savedChat.getAnswer())
                    .askedAt(savedChat.getAskedAt())
                    .build();
        } else {
            log.warn("Chat history NOT saved because AI response indicated failure (reason: {})",
                    aiResult.getFailureReason());
            return ChatResponseDto.builder()
                    .id(null)
                    .documentId(document != null ? document.getId() : null)
                    .question(request.getQuestion())
                    .answer(aiResult.getAnswer())
                    .askedAt(LocalDateTime.now())
                    .build();
        }
    }

    /**
     * Retrieves the chat history for a specific document, scoped to the
     * authenticated user.
     *
     * @param documentId the document ID
     * @param user       the authenticated user
     * @return list of chat response DTOs
     */
    public List<ChatResponseDto> getChatHistory(Long documentId, User user) {
        if (documentId != null) {
            documentService.getDocumentById(documentId, user);
            return chatHistoryRepository.findByUserIdAndDocumentIdOrderByAskedAtDesc(user.getId(), documentId).stream()
                    .map(this::mapToDto)
                    .collect(Collectors.toList());
        }
        return getAllUserChatHistory(user);
    }

    public List<ChatResponseDto> getAllUserChatHistory(User user) {
        return chatHistoryRepository.findByUserIdOrderByAskedAtDesc(user.getId()).stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public void deleteChat(Long chatId, User user) throws ResourceNotFoundException {
        ChatHistory chat = chatHistoryRepository.findById(chatId)
                .orElseThrow(() -> new ResourceNotFoundException("Chat not found"));

        if (!chat.getUser().getId().equals(user.getId())) {
            throw new ResourceNotFoundException("Chat not found");
        }

        chatHistoryRepository.deleteById(chatId);
    }

    @Transactional
    public void deleteChatsByDocument(Long documentId, User user) {
        documentService.getDocumentById(documentId, user);
        chatHistoryRepository.deleteByUserIdAndDocumentId(user.getId(), documentId);
    }

    private ChatResponseDto mapToDto(ChatHistory chatHistory) {
        return ChatResponseDto.builder()
                .id(chatHistory.getId())
                .documentId(chatHistory.getDocument() != null ? chatHistory.getDocument().getId() : null)
                .question(chatHistory.getQuestion())
                .answer(chatHistory.getAnswer())
                .askedAt(chatHistory.getAskedAt())
                .build();
    }
}

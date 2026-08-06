package com.aidocqa.service;

import com.aidocqa.dto.ChatRequestDto;
import com.aidocqa.dto.ChatResponseDto;
import com.aidocqa.entity.ChatHistory;
import com.aidocqa.entity.Document;
import com.aidocqa.entity.User;
import com.aidocqa.exception.DocumentNotFoundException;
import com.aidocqa.repository.ChatHistoryRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
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
     * Processes a user question against a document and returns the AI-generated answer.
     * Extracts full PDF content from disk for rich context answering.
     *
     * @param request the chat request containing documentId and question
     * @param user    the authenticated user
     * @return the chat response DTO with the answer
     */
    public ChatResponseDto askQuestion(ChatRequestDto request, User user) {
        // Fetch the document (scoped to user)
        Document document = documentService.getDocumentById(request.getDocumentId(), user);

        String contextText = null;

        // Try extracting full text from the saved PDF file for rich context
        try {
            if (document.getFilePath() != null) {
                File pdfFile = new File(document.getFilePath());
                if (pdfFile.exists()) {
                    contextText = pdfExtractorService.extractText(pdfFile);
                }
            }
        } catch (Exception e) {
            log.warn("Could not re-extract text from file path {}, using stored text/summary: {}", document.getFilePath(), e.getMessage());
        }

        // Fallback to stored text/summary if file re-extraction fails or returns empty
        if (contextText == null || contextText.isBlank()) {
            contextText = document.getExtractedText();
        }

        if (contextText == null || contextText.isBlank()) {
            throw new DocumentNotFoundException(
                    "No text content found for document ID: " + request.getDocumentId()
            );
        }

        log.info("Processing question for document ID: {} by user: {}", request.getDocumentId(), user.getEmail());

        // Call Gemini API
        String answer = geminiApiService.generateAnswer(
                contextText,
                request.getQuestion()
        );

        // Save chat history
        ChatHistory chatHistory = ChatHistory.builder()
                .document(document)
                .user(user)
                .question(request.getQuestion())
                .answer(answer)
                .build();

        ChatHistory savedChat = chatHistoryRepository.save(chatHistory);
        log.info("Chat history saved with ID: {}", savedChat.getId());

        return mapToDto(savedChat);
    }

    /**
     * Retrieves the chat history for a specific document, scoped to the authenticated user.
     *
     * @param documentId the document ID
     * @param user       the authenticated user
     * @return list of chat response DTOs
     */
    public List<ChatResponseDto> getChatHistory(Long documentId, User user) {
        // Verify the document exists and belongs to user
        documentService.getDocumentById(documentId, user);

        return chatHistoryRepository.findByUserIdAndDocumentIdOrderByAskedAtDesc(user.getId(), documentId).stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public void deleteChat(Long chatId, User user) throws ResourceNotFoundException {
        ChatHistory chat = chatHistoryRepository.findById(chatId)
                .orElseThrow(() -> new ResourceNotFoundException("Chat not found"));

        // Verify ownership
        if (!chat.getUser().getId().equals(user.getId())) {
            throw new ResourceNotFoundException("Chat not found");
        }

        chatHistoryRepository.deleteById(chatId);
    }

    @Transactional
    public void deleteChatsByDocument(Long documentId, User user) {
        // Verify document ownership
        documentService.getDocumentById(documentId, user);

        chatHistoryRepository.deleteByUserIdAndDocumentId(user.getId(), documentId);
    }

    private ChatResponseDto mapToDto(ChatHistory chatHistory) {
        return ChatResponseDto.builder()
                .id(chatHistory.getId())
                .documentId(chatHistory.getDocument().getId())
                .question(chatHistory.getQuestion())
                .answer(chatHistory.getAnswer())
                .askedAt(chatHistory.getAskedAt())
                .build();
    }
}

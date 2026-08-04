package com.aidocqa.service;

import com.aidocqa.dto.ChatRequestDto;
import com.aidocqa.dto.ChatResponseDto;
import com.aidocqa.entity.ChatHistory;
import com.aidocqa.entity.Document;
import com.aidocqa.exception.DocumentNotFoundException;
import com.aidocqa.repository.ChatHistoryRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatHistoryRepository chatHistoryRepository;
    private final DocumentService documentService;
    private final GeminiApiService geminiApiService;

    /**
     * Processes a user question against a document and returns the AI-generated answer.
     *
     * @param request the chat request containing documentId and question
     * @return the chat response DTO with the answer
     */
    public ChatResponseDto askQuestion(ChatRequestDto request) {
        // Fetch the document
        Document document = documentService.getDocumentById(request.getDocumentId());

        if (document.getExtractedText() == null || document.getExtractedText().isBlank()) {
            throw new DocumentNotFoundException(
                    "No text content found for document ID: " + request.getDocumentId()
            );
        }

        log.info("Processing question for document ID: {}", request.getDocumentId());

        // Call Gemini API
        String answer = geminiApiService.generateAnswer(
                document.getExtractedText(),
                request.getQuestion()
        );

        // Save chat history
        ChatHistory chatHistory = ChatHistory.builder()
                .document(document)
                .question(request.getQuestion())
                .answer(answer)
                .build();

        ChatHistory savedChat = chatHistoryRepository.save(chatHistory);
        log.info("Chat history saved with ID: {}", savedChat.getId());

        return mapToDto(savedChat);
    }

    /**
     * Retrieves the chat history for a specific document.
     *
     * @param documentId the document ID
     * @return list of chat response DTOs
     */
    public List<ChatResponseDto> getChatHistory(Long documentId) {
        // Verify the document exists
        documentService.getDocumentById(documentId);

        return chatHistoryRepository.findByDocumentIdOrderByAskedAtDesc(documentId).stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
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

    @Transactional
    public void deleteChat(Long chatId) throws ResourceNotFoundException {

        if (!chatHistoryRepository.existsById(chatId)) {
            throw new ResourceNotFoundException("Chat not found");
        }

        chatHistoryRepository.deleteById(chatId);
    }

    @Transactional
    public void deleteChatsByDocument(Long documentId) {

        chatHistoryRepository.deleteByDocumentId(documentId);
    }
}

package com.aidocqa.controller;

import com.aidocqa.dto.ApiResponseDto;
import com.aidocqa.dto.ChatRequestDto;
import com.aidocqa.dto.ChatResponseDto;
import com.aidocqa.service.ChatService;
import com.aidocqa.service.ResourceNotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@Tag(name = "AI Chat", description = "APIs for asking AI-powered questions about uploaded documents")
public class ChatController {

    private final ChatService chatService;

    @PostMapping("/ask")
    @Operation(summary = "Ask a question", description = "Ask an AI-powered question about a specific document")
    public ResponseEntity<ApiResponseDto<ChatResponseDto>> askQuestion(
            @Valid @RequestBody ChatRequestDto request) {

        ChatResponseDto response = chatService.askQuestion(request);
        return ResponseEntity
                .ok(ApiResponseDto.success("Question answered successfully", response));
    }

    @GetMapping("/history/{documentId}")
    @Operation(summary = "Get chat history", description = "Retrieve the chat history for a specific document")
    public ResponseEntity<ApiResponseDto<List<ChatResponseDto>>> getChatHistory(
            @PathVariable Long documentId) {

        List<ChatResponseDto> history = chatService.getChatHistory(documentId);
        return ResponseEntity
                .ok(ApiResponseDto.success("Chat history retrieved successfully", history));
    }

    @DeleteMapping("/{chatId}")
    @Operation(summary = "Delete chat by ID")
    public ResponseEntity<ApiResponseDto<Void>> deleteChat(
            @PathVariable Long chatId) throws ResourceNotFoundException {

        chatService.deleteChat(chatId);

        return ResponseEntity.ok(
                ApiResponseDto.success("Chat deleted successfully", null)
        );
    }

    @DeleteMapping("/document/{documentId}")
    @Operation(summary = "Delete all chats for a document")
    public ResponseEntity<ApiResponseDto<Void>> deleteChatsByDocument(
            @PathVariable Long documentId) {

        chatService.deleteChatsByDocument(documentId);

        return ResponseEntity.ok(
                ApiResponseDto.success("All chats deleted successfully", null)
        );
    }
}

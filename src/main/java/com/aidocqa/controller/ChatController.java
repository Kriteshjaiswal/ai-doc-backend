package com.aidocqa.controller;

import com.aidocqa.dto.ApiResponseDto;
import com.aidocqa.dto.ChatRequestDto;
import com.aidocqa.dto.ChatResponseDto;
import com.aidocqa.exception.ResourceNotFoundException;
import com.aidocqa.security.UserPrincipal;
import com.aidocqa.service.ChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@Tag(name = "AI Chat", description = "APIs for asking AI-powered questions about uploaded documents")
public class ChatController {

    private final ChatService chatService;

    @PostMapping("/ask")
    @Operation(summary = "Ask a question", description = "Ask an AI-powered question about a specific document")
    public ResponseEntity<ApiResponseDto<ChatResponseDto>> askQuestion(
            @Valid @RequestBody ChatRequestDto request,
            @AuthenticationPrincipal UserPrincipal user) {

        String userEmail = user != null ? user.getEmail() : "anonymous";
        Long userId = user != null ? user.getId() : null;

        log.info("🚀 [AI-CHAT] Incoming Sync Question: \"{}\" | docId: {} | user: {}",
                request.getQuestion(), request.getDocumentId(), userEmail);

        ChatResponseDto response = chatService.askQuestion(request, user);
        return ResponseEntity
                .ok(ApiResponseDto.success("Question answered successfully", response));
    }

    @GetMapping(value = "/ask/stream", produces = org.springframework.http.MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Ask question with real-time SSE token streaming", description = "Streams tokens progressively (< 300ms Time-To-First-Token)")
    public org.springframework.web.servlet.mvc.method.annotation.SseEmitter askQuestionStream(
            @RequestParam(required = false) Long documentId,
            @RequestParam String question,
            @RequestParam(required = false, defaultValue = "MEDIUM") String responseDepth,
            @AuthenticationPrincipal UserPrincipal user) {

        String userEmail = user != null ? user.getEmail() : "anonymous";
        log.info("⚡ [AI-CHAT-STREAM] Incoming Stream Request: \"{}\" | docId: {} | depth: {} | user: {}",
                question, documentId, responseDepth, userEmail);

        org.springframework.web.servlet.mvc.method.annotation.SseEmitter emitter =
                new org.springframework.web.servlet.mvc.method.annotation.SseEmitter(120_000L); // 2 minute timeout

        chatService.askQuestionStream(documentId, question, responseDepth, user, emitter);
        return emitter;
    }

    @GetMapping({ "/history", "/history/{documentId}" })
    @Operation(summary = "Get chat history", description = "Retrieve the chat history for a specific document or all documents")
    public ResponseEntity<ApiResponseDto<List<ChatResponseDto>>> getChatHistory(
            @PathVariable(required = false) Long documentId,
            @AuthenticationPrincipal UserPrincipal user) {

        log.info("📖 [AI-CHAT-CONTROLLER] Fetching chat history for documentId: {}, user: {}",
                documentId, (user != null ? user.getEmail() : "anonymous"));

        List<ChatResponseDto> history = chatService.getChatHistory(documentId, user);
        return ResponseEntity
                .ok(ApiResponseDto.success("Chat history retrieved successfully", history));
    }

    @DeleteMapping("/{chatId}")
    @Operation(summary = "Delete chat by ID")
    public ResponseEntity<ApiResponseDto<Void>> deleteChat(
            @PathVariable Long chatId,
            @AuthenticationPrincipal UserPrincipal user) throws ResourceNotFoundException {

        chatService.deleteChat(chatId, user);

        return ResponseEntity.ok(
                ApiResponseDto.success("Chat deleted successfully", null));
    }

    @DeleteMapping("/document/{documentId}")
    @Operation(summary = "Delete all chats for a document")
    public ResponseEntity<ApiResponseDto<Void>> deleteChatsByDocument(
            @PathVariable Long documentId,
            @AuthenticationPrincipal UserPrincipal user) {

        chatService.deleteChatsByDocument(documentId, user);

        return ResponseEntity.ok(
                ApiResponseDto.success("All chats deleted successfully", null));
    }
}

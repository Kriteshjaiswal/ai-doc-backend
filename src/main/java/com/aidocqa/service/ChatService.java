package com.aidocqa.service;

import com.aidocqa.dto.ChatRequestDto;
import com.aidocqa.dto.ChatResponseDto;
import com.aidocqa.dto.GeminiResponseDto;
import com.aidocqa.entity.ChatHistory;
import com.aidocqa.entity.Document;
import com.aidocqa.exception.ResourceNotFoundException;
import com.aidocqa.repository.ChatHistoryRepository;
import com.aidocqa.security.UserPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.time.LocalDateTime;
import java.util.*;
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
     */
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final java.util.concurrent.ExecutorService sseExecutor =
            java.util.concurrent.Executors.newThreadPerTaskExecutor(
                    Thread.ofVirtual().name("ai-sse-stream-", 1).factory());

    /**
     * Processes a user question against an optional document or general knowledge,
     * returning the AI answer.
     * Persists to chat history ONLY if the AI response execution was successful.
     */
    public ChatResponseDto askQuestion(ChatRequestDto request, UserPrincipal user) {
        Document document = null;
        if (request.getDocumentId() != null) {
            try {
                document = documentService.getDocumentById(request.getDocumentId(), user);
            } catch (Exception e) {
                log.warn("Could not retrieve document ID {}: {}", request.getDocumentId(), e.getMessage());
            }
        }

        Long currentUserId = (user != null && user.getId() != null) ? user.getId() : 1L;

        log.info("🔥 [AI-CHAT-SERVICE] Executing Question: '{}' | docId: {} | userId: {}",
                request.getQuestion(), request.getDocumentId(), currentUserId);

        // Retrieve top 5 recent conversation turns at DB query level (fast, bounded)
        List<ChatHistory> recentHistory;
        if (request.getDocumentId() != null) {
            recentHistory = chatHistoryRepository.findTop5ByUserIdAndDocumentIdOrderByAskedAtDesc(currentUserId, request.getDocumentId());
        } else {
            recentHistory = chatHistoryRepository.findTop5ByUserIdOrderByAskedAtDesc(currentUserId);
        }

        String contextText = buildSmartDocumentContext(document, request.getQuestion(), recentHistory);
        List<String> pageImagesBase64 = null;

        // Call Gemini AI API with smart context and conversation history
        GeminiResponseDto aiResult = geminiApiService.generateAnswerMultimodal(contextText, pageImagesBase64,
                request.getQuestion(), recentHistory, request.getResponseDepth());

        log.info("🎯 [AI-CHAT-SERVICE] AI Result: provider={}, model={}, depth={}, success={}, grounded={}, answerLength={}",
                aiResult.getProvider(), aiResult.getModel(), request.getResponseDepth(), aiResult.isSuccess(), aiResult.isGrounded(),
                (aiResult.getAnswer() != null ? aiResult.getAnswer().length() : 0));

        // Save chat history ONLY when success == true and answer is non-empty
        String depthToSave = (request.getResponseDepth() != null && !request.getResponseDepth().isBlank())
                ? request.getResponseDepth().toUpperCase() : "MEDIUM";

        if (aiResult.isSuccess() && aiResult.getAnswer() != null && !aiResult.getAnswer().isBlank()) {
            ChatHistory chatHistory = ChatHistory.builder()
                    .document(document)
                    .userId(currentUserId)
                    .question(request.getQuestion())
                    .answer(aiResult.getAnswer())
                    .responseDepth(depthToSave)
                    .build();

            ChatHistory savedChat = chatHistoryRepository.save(chatHistory);
            log.info("✅ [AI-CHAT-SERVICE] Chat history saved with ID: {}", savedChat.getId());

            return ChatResponseDto.builder()
                    .id(savedChat.getId())
                    .documentId(document != null ? document.getId() : null)
                    .question(savedChat.getQuestion())
                    .answer(savedChat.getAnswer())
                    .responseDepth(savedChat.getResponseDepth())
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
                    .responseDepth(depthToSave)
                    .askedAt(LocalDateTime.now())
                    .build();
        }
    }

    /**
     * Backward-compatible streaming overload.
     */
    public void askQuestionStream(Long documentId, String question, UserPrincipal user, org.springframework.web.servlet.mvc.method.annotation.SseEmitter emitter) {
        askQuestionStream(documentId, question, "MEDIUM", user, emitter);
    }

    /**
     * Real-time Server-Sent Events (SSE) AI streaming execution with explicit response depth (LOW, MEDIUM, HIGH).
     * Streams word/token chunks to the client as they arrive from Gemini (< 300ms TTFT).
     */
    public void askQuestionStream(Long documentId, String question, String responseDepth, UserPrincipal user, org.springframework.web.servlet.mvc.method.annotation.SseEmitter emitter) {
        Long currentUserId = (user != null && user.getId() != null) ? user.getId() : 1L;

        Document document = null;
        if (documentId != null) {
            try {
                document = documentService.getDocumentById(documentId, user);
            } catch (Exception e) {
                log.warn("Stream: could not retrieve document ID {}: {}", documentId, e.getMessage());
            }
        }

        // Fetch bounded recent history at DB level
        List<ChatHistory> recentHistory;
        if (documentId != null) {
            recentHistory = chatHistoryRepository.findTop5ByUserIdAndDocumentIdOrderByAskedAtDesc(currentUserId, documentId);
        } else {
            recentHistory = chatHistoryRepository.findTop5ByUserIdOrderByAskedAtDesc(currentUserId);
        }

        final Document targetDoc = document;
        final String contextText = buildSmartDocumentContext(targetDoc, question, recentHistory);
        final String requestId = UUID.randomUUID().toString().substring(0, 8);
        final String userIdentifier = (user != null && user.getEmail() != null) ? user.getEmail() : ("user-" + currentUserId);

        sseExecutor.submit(() -> {
            try {
                // Send initial connection event
                emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event()
                        .name("start")
                        .data("{\"status\":\"started\"}"));

                GeminiResponseDto aiResult = geminiApiService.streamAnswerMultimodal(
                        requestId,
                        userIdentifier,
                        contextText,
                        null,
                        question,
                        recentHistory,
                        chunk -> {
                            try {
                                String chunkPayload = objectMapper.writeValueAsString(Map.of("chunk", chunk));
                                emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event()
                                        .name("chunk")
                                        .data(chunkPayload));
                            } catch (Exception e) {
                                log.debug("Error sending SSE chunk: {}", e.getMessage());
                            }
                        },
                        responseDepth
                );

                // Persist chat history if successful
                Long savedId = null;
                String depthToSave = (responseDepth != null && !responseDepth.isBlank())
                        ? responseDepth.toUpperCase() : "MEDIUM";

                if (aiResult.isSuccess() && aiResult.getAnswer() != null && !aiResult.getAnswer().isBlank()) {
                    ChatHistory chatHistory = ChatHistory.builder()
                            .document(targetDoc)
                            .userId(currentUserId)
                            .question(question)
                            .answer(aiResult.getAnswer())
                            .responseDepth(depthToSave)
                            .build();
                    ChatHistory saved = chatHistoryRepository.save(chatHistory);
                    savedId = saved.getId();
                    log.info("✅ [AI-STREAM] Chat history saved with ID: {}", savedId);
                }

                // Send completion event
                Map<String, Object> completePayload = new HashMap<>();
                completePayload.put("id", savedId);
                completePayload.put("documentId", documentId);
                completePayload.put("provider", aiResult.getProvider());
                completePayload.put("model", aiResult.getModel());
                completePayload.put("answer", aiResult.getAnswer());
                completePayload.put("responseDepth", depthToSave);

                emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event()
                        .name("complete")
                        .data(objectMapper.writeValueAsString(completePayload)));
                emitter.complete();

            } catch (Exception ex) {
                log.error("SSE stream encountered error: {}", ex.getMessage());
                try {
                    emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event()
                            .name("error")
                            .data("{\"error\":\"" + ex.getMessage().replace("\"", "'") + "\"}"));
                } catch (Exception ignored) {}
                emitter.completeWithError(ex);
            }
        });
    }

    /**
     * Smart Context Windowing (RAG).
     * Extracts relevant document sections based on keyword relevance and conversation follow-up context.
     * Keeps payload lean (< 6000-8000 chars) for maximum Gemini processing speed and lowest TTFT.
     */
    private String buildSmartDocumentContext(Document document, String question, List<ChatHistory> recentHistory) {
        if (document == null) return "";

        String extracted = document.getExtractedText();
        if (extracted == null || extracted.isBlank()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Document: ").append(document.getFileName())
          .append(" | Pages: ").append(document.getPageCount() != null ? document.getPageCount() : 1).append("\n");
        if (document.getSummary() != null && !document.getSummary().isBlank()) {
            sb.append("Executive Overview: ").append(document.getSummary()).append("\n");
        }
        sb.append("\n");

        // If content is already concise (< 7000 chars), pass full text
        if (extracted.length() <= 7000) {
            sb.append(extracted);
            return sb.toString();
        }

        // Split by page headers: matches "--- PDF PAGE", "--- PAGE", "=== PAGE", etc.
        List<String> pageBlocks = new ArrayList<>(Arrays.asList(extracted.split("(?=(?:--- (?:PDF )?PAGE |=== (?:PDF )?PAGE |(?i)===+ (?:page|section)? \\d+))")));

        // If no page headers found or single giant block, split by paragraph boundaries into manageable chunks (~2500 chars)
        if (pageBlocks.size() <= 1) {
            pageBlocks.clear();
            String[] paras = extracted.split("\\r?\\n\\s*\\r?\\n");
            StringBuilder currentChunk = new StringBuilder();
            for (String p : paras) {
                if (currentChunk.length() + p.length() > 2500) {
                    if (currentChunk.length() > 0) {
                        pageBlocks.add(currentChunk.toString().trim());
                        currentChunk.setLength(0);
                    }
                }
                if (currentChunk.length() > 0) currentChunk.append("\n\n");
                currentChunk.append(p.trim());
            }
            if (currentChunk.length() > 0) {
                pageBlocks.add(currentChunk.toString().trim());
            }
        }

        String lowerQ = question != null ? question.toLowerCase() : "";

        // 1. If user is asking for overall document summary, provide overview + TOC + Intro + Conclusion
        boolean isWholeDocSummary = lowerQ.contains("summary") || lowerQ.contains("summarize") ||
                                    lowerQ.contains("what is this document") || lowerQ.contains("what is this book");
        if (isWholeDocSummary) {
            sb.append("Key Sections & Outline:\n");
            // First 3 pages (TOC & Preface)
            for (int i = 0; i < Math.min(3, pageBlocks.size()); i++) {
                sb.append(pageBlocks.get(i).trim()).append("\n\n");
            }
            // Last page (Conclusion) if long
            if (pageBlocks.size() > 4) {
                sb.append("Final Section / Conclusion:\n");
                sb.append(pageBlocks.get(pageBlocks.size() - 1).trim()).append("\n\n");
            }
            return sb.toString();
        }

        // 2. Keyword extraction & Follow-up augmentation
        Set<String> stopWords = Set.of("what", "is", "the", "a", "an", "this", "about", "in", "on", "for", "where",
                "how", "who", "when", "why", "to", "of", "and", "or", "can", "you", "tell", "kya", "hai", "batao", "kaise",
                "me", "it", "them", "these", "those", "explain", "give", "example", "do", "karo", "bettar", "better", "more",
                "response", "answer");

        String cleanQ = lowerQ.replaceAll("[^a-zA-Z0-9 ]", " ");
        Set<String> keywords = new HashSet<>(Arrays.stream(cleanQ.split("\\s+"))
                .map(String::trim)
                .filter(w -> w.length() > 2 && !stopWords.contains(w))
                .toList());

        // If query is a follow-up (e.g. "give me more better response", "why?", "explain this"), augment from previous turn
        if (keywords.isEmpty() && recentHistory != null && !recentHistory.isEmpty()) {
            ChatHistory lastTurn = recentHistory.get(0);
            if (lastTurn.getQuestion() != null) {
                String cleanLast = lastTurn.getQuestion().toLowerCase().replaceAll("[^a-zA-Z0-9 ]", " ");
                Arrays.stream(cleanLast.split("\\s+"))
                        .map(String::trim)
                        .filter(w -> w.length() > 2 && !stopWords.contains(w))
                        .forEach(keywords::add);
            }
        }

        class ScoredBlock {
            final int index;
            final String content;
            final int score;
            ScoredBlock(int index, String content, int score) {
                this.index = index;
                this.content = content;
                this.score = score;
            }
        }

        List<ScoredBlock> scored = new ArrayList<>();
        for (int i = 0; i < pageBlocks.size(); i++) {
            String block = pageBlocks.get(i);
            String lowerBlock = block.toLowerCase();
            int score = 0;
            for (String kw : keywords) {
                if (lowerBlock.contains(kw)) {
                    score += 2;
                }
            }
            if (score > 0) {
                scored.add(new ScoredBlock(i, block, score));
            }
        }

        sb.append("Relevant Document Passages:\n");
        int maxContextChars = 10000;
        int currentChars = sb.length();

        if (!scored.isEmpty()) {
            scored.sort((a, b) -> Integer.compare(b.score, a.score));
            // Limit to top 4 highest-scoring passages to keep TTFT ultra-fast
            List<ScoredBlock> topBlocks = scored.stream().limit(4)
                    .sorted(Comparator.comparingInt(b -> b.index))
                    .toList();

            for (ScoredBlock b : topBlocks) {
                String trimmed = b.content.trim();
                if (currentChars + trimmed.length() > maxContextChars) {
                    int remaining = maxContextChars - currentChars;
                    if (remaining > 200) {
                        sb.append(trimmed.substring(0, remaining)).append("...\n\n");
                    }
                    break;
                }
                sb.append(trimmed).append("\n\n");
                currentChars = sb.length();
            }
        } else {
            // Fallback to first 2-3 pages if no keyword matched
            for (int i = 0; i < Math.min(3, pageBlocks.size()); i++) {
                String trimmed = pageBlocks.get(i).trim();
                if (currentChars + trimmed.length() > maxContextChars) break;
                sb.append(trimmed).append("\n\n");
                currentChars = sb.length();
            }
        }

        return sb.toString();
    }

    /**
     * Retrieves the chat history for a specific document, scoped to the
     * authenticated user.
     */
    public List<ChatResponseDto> getChatHistory(Long documentId, UserPrincipal user) {
        if (documentId != null) {
            documentService.getDocumentById(documentId, user);
            return chatHistoryRepository.findByUserIdAndDocumentIdOrderByAskedAtAsc(user.getId(), documentId).stream()
                    .map(this::mapToDto)
                    .collect(Collectors.toList());
        }
        return getAllUserChatHistory(user);
    }

    public List<ChatResponseDto> getAllUserChatHistory(UserPrincipal user) {
        return chatHistoryRepository.findByUserIdOrderByAskedAtAsc(user.getId()).stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public void deleteChat(Long chatId, UserPrincipal user) throws ResourceNotFoundException {
        ChatHistory chat = chatHistoryRepository.findById(chatId)
                .orElseThrow(() -> new ResourceNotFoundException("Chat not found"));

        if (!chat.getUserId().equals(user.getId())) {
            throw new ResourceNotFoundException("Chat not found");
        }

        chatHistoryRepository.deleteById(chatId);
    }

    @Transactional
    public void deleteChatsByDocument(Long documentId, UserPrincipal user) {
        documentService.getDocumentById(documentId, user);
        chatHistoryRepository.deleteByUserIdAndDocumentId(user.getId(), documentId);
    }

    private ChatResponseDto mapToDto(ChatHistory chatHistory) {
        return ChatResponseDto.builder()
                .id(chatHistory.getId())
                .documentId(chatHistory.getDocument() != null ? chatHistory.getDocument().getId() : null)
                .question(chatHistory.getQuestion())
                .answer(chatHistory.getAnswer())
                .responseDepth(chatHistory.getResponseDepth())
                .askedAt(chatHistory.getAskedAt())
                .build();
    }
}

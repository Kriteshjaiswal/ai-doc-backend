package com.aidocqa.service;

import com.aidocqa.dto.AiHandshakeDtos.HandshakeResponseDto;
import com.aidocqa.dto.AiHandshakeDtos.PingResponseDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class GeminiHandshakeService {

    @Value("${gemini.api.key:}")
    private String geminiApiKey;

    @Value("${gemini.primary.model:gemini-3.8-flash}")
    private String primaryModel;

    private static final String DEFAULT_PRIMARY_MODEL = "gemini-3.8-flash";
    private static final List<String> AVAILABLE_MODELS = List.of(
            "gemini-3.8-flash",
            "gemini-flash-latest",
            "gemini-3.7-flash",
            "gemini-3.6-flash",
            "gemini-3.5-flash"
    );

    public String getEffectivePrimaryModel() {
        return (primaryModel != null && !primaryModel.isBlank()) ? primaryModel.trim() : DEFAULT_PRIMARY_MODEL;
    }

    private final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    // Map to maintain warm handshake state per authenticated user ID
    private final Map<Long, HandshakeResponseDto> activeUserHandshakes = new ConcurrentHashMap<>();

    /**
     * Establishes / pre-warms the Google Gemini API connection upon user login or session creation.
     * Pre-warms HTTP/2 connection pool, TLS session, and verifies API key latency.
     */
    public HandshakeResponseDto establishHandshake(Long userId, String sessionToken) {
        long startTime = System.currentTimeMillis();
        log.info("🤝 [GEMINI-HANDSHAKE] Initiating AI neural handshake for userId: {}", userId);

        if (geminiApiKey == null || geminiApiKey.isBlank()) {
            long latencyMs = System.currentTimeMillis() - startTime;
            log.warn("⚠️ [GEMINI-HANDSHAKE] Gemini API key not configured. Ready in LOCAL NLP mode in {} ms", latencyMs);

            HandshakeResponseDto localFallback = HandshakeResponseDto.builder()
                    .status("LOCAL_READY")
                    .provider("LOCAL")
                    .activeModel("documind-local-nlp")
                    .availableModels(List.of("documind-local-nlp"))
                    .latencyMs(latencyMs)
                    .establishedAt(System.currentTimeMillis())
                    .sessionActive(true)
                    .readyForChatAndSummarize(true)
                    .message("Local analytical NLP engine is ready (Gemini API key not configured)")
                    .build();

            if (userId != null) {
                activeUserHandshakes.put(userId, localFallback);
            }
            return localFallback;
        }

        try {
            // Lightweight HTTP/2 probe to Google Gemini models API to pre-warm connection & verify key
            String targetModel = getEffectivePrimaryModel();
            String probeUrl = "https://generativelanguage.googleapis.com/v1beta/models/" + targetModel + "?key=" + geminiApiKey;
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(probeUrl))
                    .timeout(Duration.ofSeconds(4))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            long latencyMs = System.currentTimeMillis() - startTime;

            boolean isConnected = response.statusCode() == 200;
            String status = isConnected ? "CONNECTED" : "DEGRADED";
            String activeModel = isConnected ? targetModel : "documind-local-nlp";

            log.info("⏱️ [GEMINI-HANDSHAKE-LATENCY] Handshake probe took {} ms | Status: {} | HTTP: {} | Model: {} | User: {}",
                    latencyMs, status, response.statusCode(), activeModel, userId);

            HandshakeResponseDto handshakeResult = HandshakeResponseDto.builder()
                    .status(status)
                    .provider(isConnected ? "GEMINI" : "LOCAL")
                    .activeModel(activeModel)
                    .availableModels(AVAILABLE_MODELS)
                    .latencyMs(latencyMs)
                    .establishedAt(System.currentTimeMillis())
                    .sessionActive(true)
                    .readyForChatAndSummarize(true)
                    .message(isConnected
                            ? "Gemini API handshake established and pre-warmed successfully (" + latencyMs + " ms)"
                            : "Gemini handshake degraded (HTTP " + response.statusCode() + "), local NLP fallback active")
                    .build();

            if (userId != null) {
                activeUserHandshakes.put(userId, handshakeResult);
            }
            return handshakeResult;

        } catch (Exception e) {
            long latencyMs = System.currentTimeMillis() - startTime;
            log.warn("⏱️ [GEMINI-HANDSHAKE-LATENCY] Handshake probe failed in {} ms: {}. Switching to LOCAL_READY.",
                    latencyMs, e.getMessage());

            HandshakeResponseDto fallback = HandshakeResponseDto.builder()
                    .status("LOCAL_READY")
                    .provider("LOCAL")
                    .activeModel("documind-local-nlp")
                    .availableModels(AVAILABLE_MODELS)
                    .latencyMs(latencyMs)
                    .establishedAt(System.currentTimeMillis())
                    .sessionActive(true)
                    .readyForChatAndSummarize(true)
                    .message("Gemini connection timed out/failed. Local engine ready (" + latencyMs + " ms)")
                    .build();

            if (userId != null) {
                activeUserHandshakes.put(userId, fallback);
            }
            return fallback;
        }
    }

    /**
     * Retrieves the current handshake status for an authenticated user.
     */
    public HandshakeResponseDto getHandshakeStatus(Long userId) {
        if (userId != null && activeUserHandshakes.containsKey(userId)) {
            return activeUserHandshakes.get(userId);
        }
        return establishHandshake(userId, null);
    }

    /**
     * Terminates the AI handshake when user logs out or session terminates.
     */
    public HandshakeResponseDto terminateHandshake(Long userId) {
        if (userId != null) {
            activeUserHandshakes.remove(userId);
            log.info("🔌 [GEMINI-HANDSHAKE] Handshake terminated for userId: {}", userId);
        }

        return HandshakeResponseDto.builder()
                .status("DISCONNECTED")
                .provider("NONE")
                .activeModel("none")
                .availableModels(List.of())
                .latencyMs(0L)
                .establishedAt(System.currentTimeMillis())
                .sessionActive(false)
                .readyForChatAndSummarize(false)
                .message("AI Handshake session closed.")
                .build();
    }

    /**
     * Live ping test measuring current round-trip response time to Gemini in milliseconds.
     */
    public PingResponseDto ping(Long userId) {
        long startTime = System.currentTimeMillis();

        if (geminiApiKey == null || geminiApiKey.isBlank()) {
            long latencyMs = System.currentTimeMillis() - startTime;
            log.info("⏱️ [GEMINI-PING-LATENCY] Local ping took {} ms | User: {}", latencyMs, userId);
            return PingResponseDto.builder()
                    .status("LOCAL_READY")
                    .provider("LOCAL")
                    .activeModel("documind-local-nlp")
                    .latencyMs(latencyMs)
                    .timestamp(System.currentTimeMillis())
                    .message("Local analytical NLP response")
                    .build();
        }

        try {
            String targetModel = getEffectivePrimaryModel();
            String probeUrl = "https://generativelanguage.googleapis.com/v1beta/models/" + targetModel + "?key=" + geminiApiKey;
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(probeUrl))
                    .timeout(Duration.ofSeconds(4))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            long latencyMs = System.currentTimeMillis() - startTime;

            log.info("⏱️ [GEMINI-PING-LATENCY] Live ping completed in {} ms | HTTP: {} | User: {}",
                    latencyMs, response.statusCode(), userId);

            // Update user's latency in active handshake map
            if (userId != null && activeUserHandshakes.containsKey(userId)) {
                HandshakeResponseDto existing = activeUserHandshakes.get(userId);
                existing.setLatencyMs(latencyMs);
                activeUserHandshakes.put(userId, existing);
            }

            return PingResponseDto.builder()
                    .status(response.statusCode() == 200 ? "CONNECTED" : "DEGRADED")
                    .provider("GEMINI")
                    .activeModel(targetModel)
                    .latencyMs(latencyMs)
                    .timestamp(System.currentTimeMillis())
                    .message("Live ping response from Google Gemini (" + latencyMs + " ms)")
                    .build();

        } catch (Exception e) {
            long latencyMs = System.currentTimeMillis() - startTime;
            log.warn("⏱️ [GEMINI-PING-LATENCY] Ping failed in {} ms: {}", latencyMs, e.getMessage());

            return PingResponseDto.builder()
                    .status("DEGRADED")
                    .provider("LOCAL")
                    .activeModel("documind-local-nlp")
                    .latencyMs(latencyMs)
                    .timestamp(System.currentTimeMillis())
                    .message("Ping timeout. Falling back to local engine (" + latencyMs + " ms)")
                    .build();
        }
    }
}

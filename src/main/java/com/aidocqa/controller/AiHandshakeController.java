package com.aidocqa.controller;

import com.aidocqa.dto.ApiResponseDto;
import com.aidocqa.dto.AiHandshakeDtos.HandshakeResponseDto;
import com.aidocqa.dto.AiHandshakeDtos.PingResponseDto;
import com.aidocqa.security.UserPrincipal;
import com.aidocqa.service.GeminiHandshakeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping({"/api/ai/handshake", "/api/documents/handshake"})
@RequiredArgsConstructor
@Tag(name = "AI Handshake & Neural Link", description = "Endpoints for establishing, monitoring, pinging, and terminating the Google Gemini API handshake")
public class AiHandshakeController {

    private final GeminiHandshakeService handshakeService;

    @PostMapping
    @Operation(summary = "Establish AI Handshake", description = "Initiate session handshake with Gemini API to pre-warm connections and measure latency")
    public ResponseEntity<ApiResponseDto<HandshakeResponseDto>> establishHandshake(
            @AuthenticationPrincipal UserPrincipal user,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        long startTime = System.currentTimeMillis();
        Long userId = user != null ? user.getId() : null;
        log.info("📡 [API-HANDSHAKE-REQ] Received establish handshake request from userId: {}", userId);

        HandshakeResponseDto response = handshakeService.establishHandshake(userId, authHeader);
        long durationMs = System.currentTimeMillis() - startTime;

        log.info("⏱️ [API-LATENCY] establishHandshake completed in {} ms | Status: {} | Model: {}",
                durationMs, response.getStatus(), response.getActiveModel());

        return ResponseEntity.ok(ApiResponseDto.success("AI handshake established successfully", response));
    }

    @GetMapping("/status")
    @Operation(summary = "Get AI Handshake Status", description = "Retrieve current handshake connection state, latency, and active model")
    public ResponseEntity<ApiResponseDto<HandshakeResponseDto>> getHandshakeStatus(
            @AuthenticationPrincipal UserPrincipal user) {

        long startTime = System.currentTimeMillis();
        Long userId = user != null ? user.getId() : null;

        HandshakeResponseDto response = handshakeService.getHandshakeStatus(userId);
        long durationMs = System.currentTimeMillis() - startTime;

        log.info("⏱️ [API-LATENCY] getHandshakeStatus responded in {} ms | Latency: {} ms | Status: {}",
                durationMs, response.getLatencyMs(), response.getStatus());

        return ResponseEntity.ok(ApiResponseDto.success("AI handshake status retrieved", response));
    }

    @PostMapping("/terminate")
    @Operation(summary = "Terminate AI Handshake", description = "Close active AI handshake session upon user logout")
    public ResponseEntity<ApiResponseDto<HandshakeResponseDto>> terminateHandshake(
            @AuthenticationPrincipal UserPrincipal user) {

        long startTime = System.currentTimeMillis();
        Long userId = user != null ? user.getId() : null;
        log.info("🔌 [API-HANDSHAKE-REQ] Terminate handshake requested for userId: {}", userId);

        HandshakeResponseDto response = handshakeService.terminateHandshake(userId);
        long durationMs = System.currentTimeMillis() - startTime;

        log.info("⏱️ [API-LATENCY] terminateHandshake completed in {} ms for userId: {}", durationMs, userId);

        return ResponseEntity.ok(ApiResponseDto.success("AI handshake terminated successfully", response));
    }

    @GetMapping("/ping")
    @Operation(summary = "Live AI Latency Ping", description = "Probe current round-trip response time in ms from App -> Gemini API")
    public ResponseEntity<ApiResponseDto<PingResponseDto>> ping(
            @AuthenticationPrincipal UserPrincipal user) {

        long startTime = System.currentTimeMillis();
        Long userId = user != null ? user.getId() : null;

        PingResponseDto response = handshakeService.ping(userId);
        long durationMs = System.currentTimeMillis() - startTime;

        log.info("⏱️ [API-LATENCY] Ping probe completed in {} ms (Gemini RTT: {} ms)",
                durationMs, response.getLatencyMs());

        return ResponseEntity.ok(ApiResponseDto.success("AI latency ping successful", response));
    }
}

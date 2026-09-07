package com.aidocqa.dto;

import lombok.*;

import java.util.List;

public class AiHandshakeDtos {

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class HandshakeResponseDto {
        private String status;                  // "CONNECTED", "CONNECTING", "LOCAL_READY", "DISCONNECTED"
        private String provider;                // "GEMINI", "LOCAL"
        private String activeModel;             // e.g. "gemini-3.6-flash"
        private List<String> availableModels;
        private Long latencyMs;                 // Response time in milliseconds
        private Long establishedAt;             // Epoch millisecond timestamp
        private Boolean sessionActive;
        private Boolean readyForChatAndSummarize;
        private String message;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PingResponseDto {
        private String status;
        private String provider;
        private String activeModel;
        private Long latencyMs;
        private Long timestamp;
        private String message;
    }
}

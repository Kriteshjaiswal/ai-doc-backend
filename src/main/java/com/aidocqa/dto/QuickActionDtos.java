package com.aidocqa.dto;

import lombok.*;

import java.util.Map;

public class QuickActionDtos {

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class QuickActionRequestDto {
        private String action; // summarize, extract-data, find-risks, generate-notes, create-flashcards, translate
        private String targetLanguage; // e.g. "Spanish", "Hindi", "French", "German", "Japanese"
        private String scope; // full, page, section
        private Integer page;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class QuickActionResponseDto {
        private String action;
        private String title;
        private String resultText;
        private Map<String, Object> structuredData;
        private String status;
        private String message;
    }
}

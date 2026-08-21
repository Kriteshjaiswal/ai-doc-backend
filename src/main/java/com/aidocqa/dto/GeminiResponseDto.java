package com.aidocqa.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GeminiResponseDto {

    private String answer;
    private String provider;      // "GEMINI", "LOCAL", "NONE"
    private String model;         // "gemini-3.6-flash", "gemini-3.7-flash", "gemini-3.5-flash-lite", "local-engine", "none"
    private boolean success;      // true if valid AI answer generated
    private boolean grounded;     // true if grounded in document
    private String failureReason; // detail if failed
}

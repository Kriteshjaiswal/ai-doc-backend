package com.aidocqa.dto;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FlashcardResponseDto {

    private Long id;
    private Long documentId;
    private String docTitle;
    private String question;
    private String answer;
    private String difficulty;
    private String status;
    private boolean isFavorite;
    private LocalDateTime createdAt;
}

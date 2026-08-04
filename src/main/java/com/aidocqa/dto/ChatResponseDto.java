package com.aidocqa.dto;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatResponseDto {

    private Long id;
    private Long documentId;
    private String question;
    private String answer;
    private LocalDateTime askedAt;
}

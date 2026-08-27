package com.aidocqa.dto;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentNoteDto {

    private String id;
    private String title;
    private String content;
    private Integer page;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

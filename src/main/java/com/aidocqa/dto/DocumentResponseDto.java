package com.aidocqa.dto;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentResponseDto {

    private Long id;
    private String fileName;
    private Long fileSize;
    private LocalDateTime uploadedAt;
}

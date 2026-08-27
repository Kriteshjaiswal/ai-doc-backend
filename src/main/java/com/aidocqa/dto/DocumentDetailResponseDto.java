package com.aidocqa.dto;

import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentDetailResponseDto {

    private Long id;
    private String fileName;
    private Long fileSize;
    private Integer pageCount;
    private String mimeType;
    private LocalDateTime uploadedAt;
    private String analysisStatus;
    private String summary;
    private String extractedText;
    private DocumentAnalysisResponseDto analysis;

    @Builder.Default
    private List<DocumentNoteDto> notes = new ArrayList<>();

    @Builder.Default
    private List<DocumentBookmarkDto> bookmarks = new ArrayList<>();
}

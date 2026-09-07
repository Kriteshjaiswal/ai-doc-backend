package com.aidocqa.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "documents", indexes = {
    @Index(name = "idx_doc_user_uploaded", columnList = "user_id, uploaded_at DESC"),
    @Index(name = "idx_doc_user_status", columnList = "user_id, analysis_status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    @Column(name = "file_path", nullable = false)
    private String filePath;

    @Column(name = "extracted_text", columnDefinition = "TEXT")
    private String extractedText;

    @Column(name = "page_count")
    @Builder.Default
    private Integer pageCount = 1;

    @Column(name = "mime_type")
    @Builder.Default
    private String mimeType = "application/pdf";

    @Column(name = "analysis_status", nullable = false)
    @Builder.Default
    private String analysisStatus = "UPLOADED";

    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary;

    @Column(name = "analysis_json", columnDefinition = "TEXT")
    private String analysisJson;

    @Column(name = "notes_json", columnDefinition = "TEXT")
    private String notesJson;

    @Column(name = "bookmarks_json", columnDefinition = "TEXT")
    private String bookmarksJson;

    @Column(name = "quick_actions_json", columnDefinition = "TEXT")
    private String quickActionsJson;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @OneToMany(mappedBy = "document", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private java.util.List<ChatHistory> chatHistories = new java.util.ArrayList<>();

    @Column(name = "uploaded_at", nullable = false)
    private LocalDateTime uploadedAt;

    @PrePersist
    protected void onCreate() {
        this.uploadedAt = LocalDateTime.now();
        if (this.analysisStatus == null) {
            this.analysisStatus = "UPLOADED";
        }
    }
}

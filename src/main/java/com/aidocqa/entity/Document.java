package com.aidocqa.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "documents")
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

    @Lob
    @Column(name = "extracted_text", columnDefinition = "LONGTEXT")
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

    @Lob
    @Column(name = "summary", columnDefinition = "LONGTEXT")
    private String summary;

    @Lob
    @Column(name = "analysis_json", columnDefinition = "LONGTEXT")
    private String analysisJson;

    @Lob
    @Column(name = "notes_json", columnDefinition = "LONGTEXT")
    private String notesJson;

    @Lob
    @Column(name = "bookmarks_json", columnDefinition = "LONGTEXT")
    private String bookmarksJson;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

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

package com.aidocqa.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "chat_history", indexes = {
    @Index(name = "idx_chat_user_doc_time", columnList = "user_id, document_id, asked_at DESC"),
    @Index(name = "idx_chat_user_asked", columnList = "user_id, asked_at DESC")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = true)
    private Document document;

    @Column(name = "question", columnDefinition = "TEXT", nullable = false)
    private String question;

    @Column(name = "answer", columnDefinition = "TEXT", nullable = false)
    private String answer;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "response_depth", length = 20)
    private String responseDepth;

    @Column(name = "asked_at", nullable = false)
    private LocalDateTime askedAt;

    @PrePersist
    protected void onCreate() {
        this.askedAt = LocalDateTime.now();
    }
}

package com.aidocqa.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "chat_history")
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

    @Lob
    @Column(name = "question", columnDefinition = "TEXT", nullable = false)
    private String question;

    @Lob
    @Column(name = "answer", columnDefinition = "LONGTEXT", nullable = false)
    private String answer;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "asked_at", nullable = false)
    private LocalDateTime askedAt;

    @PrePersist
    protected void onCreate() {
        this.askedAt = LocalDateTime.now();
    }
}

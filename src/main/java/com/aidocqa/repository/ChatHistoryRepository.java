package com.aidocqa.repository;

import com.aidocqa.entity.ChatHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatHistoryRepository extends JpaRepository<ChatHistory, Long> {

    List<ChatHistory> findByDocumentIdOrderByAskedAtDesc(Long documentId);

    @Modifying
    void deleteByDocumentId(Long documentId);

    List<ChatHistory> findByUserIdAndDocumentIdOrderByAskedAtDesc(Long userId, Long documentId);

    @Modifying
    void deleteByUserIdAndDocumentId(Long userId, Long documentId);
}

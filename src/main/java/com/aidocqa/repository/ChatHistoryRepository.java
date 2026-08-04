package com.aidocqa.repository;

import com.aidocqa.entity.ChatHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatHistoryRepository extends JpaRepository<ChatHistory, Long> {

    List<ChatHistory> findByDocumentIdOrderByAskedAtDesc(Long documentId);
    void deleteByDocumentId(Long documentId);

}

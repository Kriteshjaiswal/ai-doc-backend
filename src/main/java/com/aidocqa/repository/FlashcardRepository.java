package com.aidocqa.repository;

import com.aidocqa.entity.Flashcard;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FlashcardRepository extends JpaRepository<Flashcard, Long> {

    List<Flashcard> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<Flashcard> findByDocumentIdAndUserIdOrderByCreatedAtDesc(Long documentId, Long userId);

    void deleteByDocumentId(Long documentId);
}

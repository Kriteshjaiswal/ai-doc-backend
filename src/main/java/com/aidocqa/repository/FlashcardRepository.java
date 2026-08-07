package com.aidocqa.repository;

import com.aidocqa.entity.Flashcard;
import com.aidocqa.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FlashcardRepository extends JpaRepository<Flashcard, Long> {

    List<Flashcard> findByUserOrderByCreatedAtDesc(User user);

    List<Flashcard> findByDocumentIdAndUserOrderByCreatedAtDesc(Long documentId, User user);

    void deleteByDocumentId(Long documentId);
}

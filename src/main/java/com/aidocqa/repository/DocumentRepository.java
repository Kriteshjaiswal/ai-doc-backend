package com.aidocqa.repository;

import com.aidocqa.entity.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentRepository extends JpaRepository<Document, Long> {

    interface DocumentSummaryProjection {
        Long getId();
        String getFileName();
        Long getFileSize();
        Integer getPageCount();
        String getMimeType();
        String getAnalysisStatus();
        String getSummary();
        java.time.LocalDateTime getUploadedAt();
    }

    @org.springframework.data.jpa.repository.Query("SELECT d.id AS id, d.fileName AS fileName, d.fileSize AS fileSize, " +
           "d.pageCount AS pageCount, d.mimeType AS mimeType, d.analysisStatus AS analysisStatus, " +
           "d.summary AS summary, d.uploadedAt AS uploadedAt " +
           "FROM Document d WHERE d.userId = :userId ORDER BY d.uploadedAt DESC")
    List<DocumentSummaryProjection> findSummaryByUserId(@org.springframework.data.repository.query.Param("userId") Long userId);

    List<Document> findByUserId(Long userId);

    Optional<Document> findByIdAndUserId(Long id, Long userId);
}

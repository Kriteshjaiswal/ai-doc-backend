package com.aidocqa.service;

import com.aidocqa.dto.DocumentResponseDto;
import com.aidocqa.entity.Document;
import com.aidocqa.entity.User;
import com.aidocqa.exception.DocumentNotFoundException;
import com.aidocqa.exception.InvalidFileException;
import com.aidocqa.repository.ChatHistoryRepository;
import com.aidocqa.repository.DocumentRepository;
import com.aidocqa.utility.FileUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final ChatHistoryRepository chatHistoryRepository;
    private final PdfExtractorService pdfExtractorService;
    private final GeminiApiService geminiApiService;

    @Value("${file.upload-dir}")
    private String uploadDir;

    /**
     * Uploads a PDF file, extracts text, generates a one-time 3-4 line AI summary,
     * and stores the summary in the database's extracted_text column.
     * Associates the document with the authenticated user.
     *
     * @param file the uploaded PDF file
     * @param user the authenticated user
     * @return the document response DTO
     */
    public DocumentResponseDto uploadDocument(MultipartFile file, User user) {
        // Validate the file
        FileUtil.validatePdfFile(file);

        try {
            // Generate unique file name and save to disk
            String uniqueFileName = FileUtil.generateUniqueFileName(file.getOriginalFilename());
            Path filePath = Paths.get(uploadDir, uniqueFileName);
            Files.copy(file.getInputStream(), filePath);

            log.info("File saved to: {}", filePath.toAbsolutePath());

            // Extract text from the PDF
            String rawText = pdfExtractorService.extractText(filePath.toFile());

            // One-time AI summary generation upon document upload
            String aiSummary = geminiApiService.generateAnswer(
                    rawText,
                    "Summarize this document in 3 to 4 concise sentences/lines focusing on key points."
            );

            log.info("One-time AI summary generated for document: {}", file.getOriginalFilename());

            // Build and save the document entity storing the AI summary in extracted_text column
            Document document = Document.builder()
                    .fileName(file.getOriginalFilename())
                    .fileSize(file.getSize())
                    .filePath(filePath.toString())
                    .extractedText(aiSummary)
                    .user(user)
                    .build();

            Document savedDocument = documentRepository.save(document);
            log.info("Document saved with ID: {} for user: {}", savedDocument.getId(), user.getEmail());

            return mapToDto(savedDocument);

        } catch (IOException e) {
            log.error("Failed to process uploaded file: {}", e.getMessage(), e);
            throw new InvalidFileException("Failed to process the uploaded file: " + e.getMessage());
        }
    }

    /**
     * Retrieves all uploaded documents for the authenticated user.
     *
     * @param user the authenticated user
     * @return list of document response DTOs
     */
    public List<DocumentResponseDto> getAllDocuments(User user) {
        return documentRepository.findByUserId(user.getId()).stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    /**
     * Deletes a document by its ID, including the stored file and all associated chat history.
     * Only deletes if the document belongs to the authenticated user.
     *
     * @param id   the document ID
     * @param user the authenticated user
     */
    @Transactional
    public void deleteDocument(Long id, User user) {
        Document document = documentRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new DocumentNotFoundException(id));

        // Delete associated chat history records first to satisfy foreign key constraints
        chatHistoryRepository.deleteByDocumentId(document.getId());
        log.info("Deleted chat history for document ID: {}", id);

        // Delete the file from disk
        try {
            Path filePath = Paths.get(document.getFilePath());
            Files.deleteIfExists(filePath);
            log.info("Deleted file from disk: {}", filePath);
        } catch (IOException e) {
            log.warn("Failed to delete file from disk: {}", e.getMessage());
        }

        // Delete from database
        documentRepository.delete(document);
        log.info("Document deleted with ID: {} by user: {}", id, user.getEmail());
    }

    /**
     * Retrieves a document entity by its ID, scoped to the authenticated user.
     *
     * @param id   the document ID
     * @param user the authenticated user
     * @return the document entity
     */
    public Document getDocumentById(Long id, User user) {
        return documentRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new DocumentNotFoundException(id));
    }

    private DocumentResponseDto mapToDto(Document document) {
        return DocumentResponseDto.builder()
                .id(document.getId())
                .fileName(document.getFileName())
                .fileSize(document.getFileSize())
                .uploadedAt(document.getUploadedAt())
                .extractedText(document.getExtractedText())
                .build();
    }
}

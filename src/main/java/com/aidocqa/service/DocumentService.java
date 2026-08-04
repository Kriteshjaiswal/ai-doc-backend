package com.aidocqa.service;

import com.aidocqa.dto.DocumentResponseDto;
import com.aidocqa.entity.Document;
import com.aidocqa.exception.DocumentNotFoundException;
import com.aidocqa.exception.InvalidFileException;
import com.aidocqa.repository.DocumentRepository;
import com.aidocqa.utility.FileUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
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
    private final PdfExtractorService pdfExtractorService;

    @Value("${file.upload-dir}")
    private String uploadDir;

    /**
     * Uploads a PDF file, extracts its text content, and stores metadata in the database.
     *
     * @param file the uploaded PDF file
     * @return the document response DTO
     */
    public DocumentResponseDto uploadDocument(MultipartFile file) {
        // Validate the file
        FileUtil.validatePdfFile(file);

        try {
            // Generate unique file name and save to disk
            String uniqueFileName = FileUtil.generateUniqueFileName(file.getOriginalFilename());
            Path filePath = Paths.get(uploadDir, uniqueFileName);
            Files.copy(file.getInputStream(), filePath);

            log.info("File saved to: {}", filePath.toAbsolutePath());

            // Extract text from the PDF
            String extractedText = pdfExtractorService.extractText(filePath.toFile());

            // Build and save the document entity
            Document document = Document.builder()
                    .fileName(file.getOriginalFilename())
                    .fileSize(file.getSize())
                    .filePath(filePath.toString())
                    .extractedText(extractedText)
                    .build();

            Document savedDocument = documentRepository.save(document);
            log.info("Document saved with ID: {}", savedDocument.getId());

            return mapToDto(savedDocument);

        } catch (IOException e) {
            log.error("Failed to process uploaded file: {}", e.getMessage(), e);
            throw new InvalidFileException("Failed to process the uploaded file: " + e.getMessage());
        }
    }

    /**
     * Retrieves all uploaded documents.
     *
     * @return list of document response DTOs
     */
    public List<DocumentResponseDto> getAllDocuments() {
        return documentRepository.findAll().stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    /**
     * Deletes a document by its ID, including the file from disk.
     *
     * @param id the document ID
     */
    public void deleteDocument(Long id) {
        Document document = documentRepository.findById(id)
                .orElseThrow(() -> new DocumentNotFoundException(id));

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
        log.info("Document deleted with ID: {}", id);
    }

    /**
     * Retrieves a document entity by its ID.
     *
     * @param id the document ID
     * @return the document entity
     */
    public Document getDocumentById(Long id) {
        return documentRepository.findById(id)
                .orElseThrow(() -> new DocumentNotFoundException(id));
    }

    private DocumentResponseDto mapToDto(Document document) {
        return DocumentResponseDto.builder()
                .id(document.getId())
                .fileName(document.getFileName())
                .fileSize(document.getFileSize())
                .uploadedAt(document.getUploadedAt())
                .build();
    }
}

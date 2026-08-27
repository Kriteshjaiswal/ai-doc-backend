package com.aidocqa.service;

import com.aidocqa.dto.*;
import com.aidocqa.dto.QuickActionDtos.*;
import com.aidocqa.entity.Document;
import com.aidocqa.entity.User;
import com.aidocqa.exception.DocumentNotFoundException;
import com.aidocqa.exception.InvalidFileException;
import com.aidocqa.repository.ChatHistoryRepository;
import com.aidocqa.repository.DocumentRepository;
import com.aidocqa.repository.FlashcardRepository;
import com.aidocqa.utility.FileUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final ChatHistoryRepository chatHistoryRepository;
    private final FlashcardRepository flashcardRepository;
    private final PdfExtractorService pdfExtractorService;
    private final DocumentAnalysisService documentAnalysisService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${file.upload-dir}")
    private String uploadDir;

    /**
     * Uploads a PDF file, calculates exact page count, extracts text,
     * triggers AI document analysis, and stores metadata in the database.
     */
    public DocumentResponseDto uploadDocument(MultipartFile file, User user) {
        FileUtil.validatePdfFile(file);

        try {
            String uniqueFileName = FileUtil.generateUniqueFileName(file.getOriginalFilename());
            Path filePath = Paths.get(uploadDir, uniqueFileName);
            Files.copy(file.getInputStream(), filePath);

            log.info("File saved to: {}", filePath.toAbsolutePath());
            File savedFile = filePath.toFile();

            // Calculate exact page count from PDF
            int pageCount = documentAnalysisService.calculatePageCount(savedFile);

            // Extract structured whole-document context
            String rawText = pdfExtractorService.extractStructuredDocContext(savedFile);
            if (rawText == null || rawText.isBlank()) {
                rawText = pdfExtractorService.extractText(savedFile);
            }

            // Create initial document entity
            Document document = Document.builder()
                    .fileName(file.getOriginalFilename())
                    .fileSize(file.getSize())
                    .filePath(filePath.toString())
                    .pageCount(pageCount)
                    .mimeType("application/pdf")
                    .analysisStatus("PROCESSING")
                    .extractedText(rawText)
                    .user(user)
                    .build();

            Document savedDocument = documentRepository.save(document);
            log.info("Document saved with ID: {} for user: {} with {} pages", savedDocument.getId(), user.getEmail(), pageCount);

            // Run AI analysis
            try {
                DocumentAnalysisResponseDto analysis = documentAnalysisService.analyzeDocument(savedDocument, savedFile);
                savedDocument.setSummary(analysis.getSummary());
                savedDocument.setAnalysisJson(objectMapper.writeValueAsString(analysis));
                savedDocument.setAnalysisStatus("COMPLETED");
                savedDocument = documentRepository.save(savedDocument);
                log.info("AI Analysis completed and saved for document ID: {}", savedDocument.getId());
            } catch (Exception e) {
                log.warn("Initial AI analysis failed for document ID {}: {}", savedDocument.getId(), e.getMessage());
                savedDocument.setAnalysisStatus("COMPLETED"); // Still mark ready with local fallback
                savedDocument = documentRepository.save(savedDocument);
            }

            return mapToDto(savedDocument);

        } catch (IOException e) {
            log.error("Failed to process uploaded file: {}", e.getMessage(), e);
            throw new InvalidFileException("Failed to process the uploaded file: " + e.getMessage());
        }
    }

    /**
     * Retrieves all uploaded documents for the authenticated user.
     */
    public List<DocumentResponseDto> getAllDocuments(User user) {
        return documentRepository.findByUserId(user.getId()).stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    /**
     * Retrieves detailed document information including AI analysis, notes, and bookmarks.
     */
    public DocumentDetailResponseDto getDocumentDetail(Long id, User user) {
        Document document = getDocumentById(id, user);
        File pdfFile = new File(document.getFilePath());

        // Parse or run analysis if missing
        DocumentAnalysisResponseDto analysisDto = null;
        if (document.getAnalysisJson() != null && !document.getAnalysisJson().isBlank()) {
            try {
                analysisDto = objectMapper.readValue(document.getAnalysisJson(), DocumentAnalysisResponseDto.class);
            } catch (Exception e) {
                log.warn("Could not parse saved analysis JSON for doc {}: {}", id, e.getMessage());
            }
        }

        if (analysisDto == null) {
            analysisDto = documentAnalysisService.analyzeDocument(document, pdfFile);
            try {
                document.setAnalysisJson(objectMapper.writeValueAsString(analysisDto));
                document.setSummary(analysisDto.getSummary());
                document.setAnalysisStatus("COMPLETED");
                documentRepository.save(document);
            } catch (Exception e) {
                log.warn("Failed to persist updated analysis JSON: {}", e.getMessage());
            }
        }

        // Parse Notes
        List<DocumentNoteDto> notes = parseNotes(document.getNotesJson());

        // Parse Bookmarks
        List<DocumentBookmarkDto> bookmarks = parseBookmarks(document.getBookmarksJson());

        return DocumentDetailResponseDto.builder()
                .id(document.getId())
                .fileName(document.getFileName())
                .fileSize(document.getFileSize())
                .pageCount(document.getPageCount() != null ? document.getPageCount() : 1)
                .mimeType(document.getMimeType() != null ? document.getMimeType() : "application/pdf")
                .uploadedAt(document.getUploadedAt())
                .analysisStatus(document.getAnalysisStatus() != null ? document.getAnalysisStatus() : "COMPLETED")
                .summary(document.getSummary())
                .extractedText(document.getExtractedText())
                .analysis(analysisDto)
                .notes(notes)
                .bookmarks(bookmarks)
                .build();
    }

    /**
     * Retrieves AI document analysis.
     */
    public DocumentAnalysisResponseDto getDocumentAnalysis(Long id, User user) {
        Document document = getDocumentById(id, user);
        if (document.getAnalysisJson() != null && !document.getAnalysisJson().isBlank()) {
            try {
                return objectMapper.readValue(document.getAnalysisJson(), DocumentAnalysisResponseDto.class);
            } catch (Exception e) {
                log.warn("Error parsing analysis JSON: {}", e.getMessage());
            }
        }

        File pdfFile = new File(document.getFilePath());
        DocumentAnalysisResponseDto analysis = documentAnalysisService.analyzeDocument(document, pdfFile);
        try {
            document.setAnalysisJson(objectMapper.writeValueAsString(analysis));
            document.setSummary(analysis.getSummary());
            document.setAnalysisStatus("COMPLETED");
            documentRepository.save(document);
        } catch (Exception e) {
            log.warn("Failed to save analysis: {}", e.getMessage());
        }
        return analysis;
    }

    /**
     * Force re-analyzes a document with fresh AI pipeline.
     */
    public DocumentAnalysisResponseDto reanalyzeDocument(Long id, User user) {
        Document document = getDocumentById(id, user);
        File pdfFile = new File(document.getFilePath());
        log.info("Triggering fresh re-analysis for document ID: {}", id);

        DocumentAnalysisResponseDto analysis = documentAnalysisService.analyzeDocument(document, pdfFile);
        try {
            document.setAnalysisJson(objectMapper.writeValueAsString(analysis));
            document.setSummary(analysis.getSummary());
            document.setAnalysisStatus("COMPLETED");
            documentRepository.save(document);
        } catch (Exception e) {
            log.error("Failed to save re-analysis: {}", e.getMessage());
        }
        return analysis;
    }

    /**
     * Renames a document.
     */
    public DocumentResponseDto renameDocument(Long id, String newFileName, User user) {
        Document document = getDocumentById(id, user);
        if (newFileName == null || newFileName.isBlank()) {
            throw new InvalidFileException("Filename cannot be blank");
        }

        String sanitized = newFileName.trim();
        if (!sanitized.toLowerCase().endsWith(".pdf")) {
            sanitized += ".pdf";
        }

        document.setFileName(sanitized);
        Document updated = documentRepository.save(document);
        log.info("Renamed document ID {} to '{}'", id, sanitized);
        return mapToDto(updated);
    }

    /**
     * Executes a quick action on the document.
     */
    public QuickActionResponseDto executeQuickAction(Long id, QuickActionRequestDto request, User user) {
        Document document = getDocumentById(id, user);
        File pdfFile = new File(document.getFilePath());
        return documentAnalysisService.executeQuickAction(document, request, pdfFile);
    }

    /**
     * Serves the raw PDF file bytes for inline browser viewing and download.
     */
    public byte[] getPdfFileBytes(Long id, User user) {
        Document document = getDocumentById(id, user);
        try {
            Path path = Paths.get(document.getFilePath());
            if (!Files.exists(path)) {
                throw new DocumentNotFoundException(id);
            }
            return Files.readAllBytes(path);
        } catch (IOException e) {
            log.error("Failed to read PDF file for doc ID {}: {}", id, e.getMessage());
            throw new InvalidFileException("Could not read PDF file: " + e.getMessage());
        }
    }

    /**
     * Renders a specific page of the PDF as a high-resolution PNG image byte array.
     */
    public byte[] renderPdfPage(Long id, int pageNumber, User user) {
        Document document = getDocumentById(id, user);
        File pdfFile = new File(document.getFilePath());

        if (!pdfFile.exists()) {
            throw new DocumentNotFoundException(id);
        }

        try (PDDocument pdDocument = Loader.loadPDF(pdfFile)) {
            int totalPages = pdDocument.getNumberOfPages();
            int pIndex = Math.max(0, Math.min(pageNumber - 1, totalPages - 1));

            PDFRenderer renderer = new PDFRenderer(pdDocument);
            BufferedImage bim = renderer.renderImageWithDPI(pIndex, 150);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(bim, "png", baos);
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Failed to render page {} for doc ID {}: {}", pageNumber, id, e.getMessage());
            throw new InvalidFileException("Failed to render PDF page: " + e.getMessage());
        }
    }

    // =========================================================================
    // NOTES & BOOKMARKS MANAGEMENT
    // =========================================================================

    public List<DocumentNoteDto> getNotes(Long id, User user) {
        Document document = getDocumentById(id, user);
        return parseNotes(document.getNotesJson());
    }

    public List<DocumentNoteDto> addNote(Long id, DocumentNoteDto newNote, User user) {
        Document document = getDocumentById(id, user);
        List<DocumentNoteDto> notes = new ArrayList<>(parseNotes(document.getNotesJson()));

        if (newNote.getId() == null || newNote.getId().isBlank()) {
            newNote.setId(UUID.randomUUID().toString());
        }
        newNote.setCreatedAt(LocalDateTime.now());
        newNote.setUpdatedAt(LocalDateTime.now());
        notes.add(0, newNote);

        try {
            document.setNotesJson(objectMapper.writeValueAsString(notes));
            documentRepository.save(document);
        } catch (Exception e) {
            log.error("Failed to save notes for doc ID {}: {}", id, e.getMessage());
        }
        return notes;
    }

    public List<DocumentNoteDto> deleteNote(Long id, String noteId, User user) {
        Document document = getDocumentById(id, user);
        List<DocumentNoteDto> notes = new ArrayList<>(parseNotes(document.getNotesJson()));
        notes.removeIf(n -> Objects.equals(n.getId(), noteId));

        try {
            document.setNotesJson(objectMapper.writeValueAsString(notes));
            documentRepository.save(document);
        } catch (Exception e) {
            log.error("Failed to save updated notes: {}", e.getMessage());
        }
        return notes;
    }

    public List<DocumentBookmarkDto> getBookmarks(Long id, User user) {
        Document document = getDocumentById(id, user);
        return parseBookmarks(document.getBookmarksJson());
    }

    public List<DocumentBookmarkDto> addBookmark(Long id, DocumentBookmarkDto newBookmark, User user) {
        Document document = getDocumentById(id, user);
        List<DocumentBookmarkDto> bookmarks = new ArrayList<>(parseBookmarks(document.getBookmarksJson()));

        if (newBookmark.getId() == null || newBookmark.getId().isBlank()) {
            newBookmark.setId(UUID.randomUUID().toString());
        }
        newBookmark.setCreatedAt(LocalDateTime.now());
        bookmarks.add(0, newBookmark);

        try {
            document.setBookmarksJson(objectMapper.writeValueAsString(bookmarks));
            documentRepository.save(document);
        } catch (Exception e) {
            log.error("Failed to save bookmarks for doc ID {}: {}", id, e.getMessage());
        }
        return bookmarks;
    }

    public List<DocumentBookmarkDto> deleteBookmark(Long id, String bookmarkId, User user) {
        Document document = getDocumentById(id, user);
        List<DocumentBookmarkDto> bookmarks = new ArrayList<>(parseBookmarks(document.getBookmarksJson()));
        bookmarks.removeIf(b -> Objects.equals(b.getId(), bookmarkId));

        try {
            document.setBookmarksJson(objectMapper.writeValueAsString(bookmarks));
            documentRepository.save(document);
        } catch (Exception e) {
            log.error("Failed to save updated bookmarks: {}", e.getMessage());
        }
        return bookmarks;
    }

    private List<DocumentNoteDto> parseNotes(String json) {
        if (json == null || json.isBlank()) return Collections.emptyList();
        try {
            return objectMapper.readValue(json, new TypeReference<List<DocumentNoteDto>>() {});
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private List<DocumentBookmarkDto> parseBookmarks(String json) {
        if (json == null || json.isBlank()) return Collections.emptyList();
        try {
            return objectMapper.readValue(json, new TypeReference<List<DocumentBookmarkDto>>() {});
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    /**
     * Deletes a document by its ID, including the stored file and all associated chat history and flashcards.
     */
    @Transactional
    public void deleteDocument(Long id, User user) {
        Document document = documentRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new DocumentNotFoundException(id));

        chatHistoryRepository.deleteByDocumentId(document.getId());
        flashcardRepository.deleteByDocumentId(document.getId());

        try {
            Path filePath = Paths.get(document.getFilePath());
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            log.warn("Failed to delete file from disk: {}", e.getMessage());
        }

        documentRepository.delete(document);
        log.info("Document deleted with ID: {} by user: {}", id, user.getEmail());
    }

    /**
     * Retrieves a document entity by its ID, scoped to the authenticated user.
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
                .pageCount(document.getPageCount() != null ? document.getPageCount() : 1)
                .analysisStatus(document.getAnalysisStatus() != null ? document.getAnalysisStatus() : "COMPLETED")
                .summary(document.getSummary())
                .uploadedAt(document.getUploadedAt())
                .extractedText(document.getExtractedText())
                .build();
    }
}

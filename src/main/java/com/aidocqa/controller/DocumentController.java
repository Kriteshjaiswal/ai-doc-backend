package com.aidocqa.controller;

import com.aidocqa.dto.*;
import com.aidocqa.dto.QuickActionDtos.*;
import com.aidocqa.security.UserPrincipal;
import com.aidocqa.service.DocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
@Tag(name = "Document Management", description = "APIs for uploading, listing, analyzing, previewing, and managing PDF documents")
public class DocumentController {

    private final DocumentService documentService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload a PDF document", description = "Upload a PDF file to extract text, calculate pages, and generate AI analysis")
    public ResponseEntity<ApiResponseDto<DocumentResponseDto>> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal UserPrincipal user) {

        DocumentResponseDto response = documentService.uploadDocument(file, user);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponseDto.success("Document uploaded and analyzed successfully", response));
    }

    @GetMapping
    @Operation(summary = "List all documents", description = "Retrieve a list of all uploaded documents for the authenticated user")
    public ResponseEntity<ApiResponseDto<List<DocumentResponseDto>>> getAllDocuments(
            @AuthenticationPrincipal UserPrincipal user) {

        List<DocumentResponseDto> documents = documentService.getAllDocuments(user);
        return ResponseEntity
                .ok(ApiResponseDto.success("Documents retrieved successfully", documents));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get document details", description = "Retrieve document metadata, AI analysis, notes, and bookmarks")
    public ResponseEntity<ApiResponseDto<DocumentDetailResponseDto>> getDocumentDetail(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal user) {

        DocumentDetailResponseDto detail = documentService.getDocumentDetail(id, user);
        return ResponseEntity
                .ok(ApiResponseDto.success("Document details retrieved successfully", detail));
    }

    @GetMapping("/{id}/analysis")
    @Operation(summary = "Get document AI analysis", description = "Retrieve complete AI extracted insights, topics, dates, financials, risks, and clauses")
    public ResponseEntity<ApiResponseDto<DocumentAnalysisResponseDto>> getDocumentAnalysis(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal user) {

        DocumentAnalysisResponseDto analysis = documentService.getDocumentAnalysis(id, user);
        return ResponseEntity
                .ok(ApiResponseDto.success("Document analysis retrieved successfully", analysis));
    }

    @PostMapping("/{id}/analyze")
    @Operation(summary = "Re-analyze document", description = "Trigger fresh AI document extraction and analysis pipeline")
    public ResponseEntity<ApiResponseDto<DocumentAnalysisResponseDto>> reanalyzeDocument(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal user) {

        DocumentAnalysisResponseDto analysis = documentService.reanalyzeDocument(id, user);
        return ResponseEntity
                .ok(ApiResponseDto.success("Document re-analyzed successfully", analysis));
    }

    @PatchMapping("/{id}/rename")
    @Operation(summary = "Rename document", description = "Update the display filename of an uploaded document")
    public ResponseEntity<ApiResponseDto<DocumentResponseDto>> renameDocument(
            @PathVariable Long id,
            @Valid @RequestBody DocumentRenameRequestDto request,
            @AuthenticationPrincipal UserPrincipal user) {

        DocumentResponseDto updated = documentService.renameDocument(id, request.getNewFileName(), user);
        return ResponseEntity
                .ok(ApiResponseDto.success("Document renamed successfully", updated));
    }

    @PostMapping("/{id}/quick-action")
    @Operation(summary = "Execute quick action", description = "Run AI quick actions such as summarize, extract-data, find-risks, generate-notes, create-flashcards, translate")
    public ResponseEntity<ApiResponseDto<QuickActionResponseDto>> executeQuickAction(
            @PathVariable Long id,
            @RequestBody QuickActionRequestDto request,
            @AuthenticationPrincipal UserPrincipal user) {

        QuickActionResponseDto response = documentService.executeQuickAction(id, request, user);
        return ResponseEntity
                .ok(ApiResponseDto.success("Quick action executed successfully", response));
    }

    @GetMapping("/{id}/file")
    @Operation(summary = "Get PDF file stream", description = "Stream the raw PDF file for inline preview or download")
    public ResponseEntity<byte[]> getPdfFile(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal user) {

        byte[] fileBytes = documentService.getPdfFileBytes(id, user);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentLength(fileBytes.length);
        headers.setCacheControl(CacheControl.maxAge(30, java.util.concurrent.TimeUnit.MINUTES).cachePublic());
        headers.setContentDisposition(ContentDisposition.inline().filename("document_" + id + ".pdf").build());
        return new ResponseEntity<>(fileBytes, headers, HttpStatus.OK);
    }

    @GetMapping("/{id}/pages/{pageNumber}")
    @Operation(summary = "Render PDF page as PNG", description = "Render high-resolution PNG image of a specific PDF page")
    public ResponseEntity<byte[]> renderPdfPage(
            @PathVariable Long id,
            @PathVariable int pageNumber,
            @AuthenticationPrincipal UserPrincipal user) {

        byte[] imageBytes = documentService.renderPdfPage(id, pageNumber, user);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.IMAGE_PNG);
        headers.setContentLength(imageBytes.length);
        headers.setCacheControl(CacheControl.maxAge(3600, java.util.concurrent.TimeUnit.SECONDS).cachePublic());
        return new ResponseEntity<>(imageBytes, headers, HttpStatus.OK);
    }

    // Notes APIs
    @GetMapping("/{id}/notes")
    @Operation(summary = "Get document notes", description = "Retrieve all user notes for this document")
    public ResponseEntity<ApiResponseDto<List<DocumentNoteDto>>> getNotes(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal user) {

        List<DocumentNoteDto> notes = documentService.getNotes(id, user);
        return ResponseEntity.ok(ApiResponseDto.success("Notes retrieved successfully", notes));
    }

    @PostMapping("/{id}/notes")
    @Operation(summary = "Add note to document", description = "Add a new note associated with this document")
    public ResponseEntity<ApiResponseDto<List<DocumentNoteDto>>> addNote(
            @PathVariable Long id,
            @RequestBody DocumentNoteDto noteDto,
            @AuthenticationPrincipal UserPrincipal user) {

        List<DocumentNoteDto> notes = documentService.addNote(id, noteDto, user);
        return ResponseEntity.ok(ApiResponseDto.success("Note added successfully", notes));
    }

    @DeleteMapping("/{id}/notes/{noteId}")
    @Operation(summary = "Delete note", description = "Remove a note from this document")
    public ResponseEntity<ApiResponseDto<List<DocumentNoteDto>>> deleteNote(
            @PathVariable Long id,
            @PathVariable String noteId,
            @AuthenticationPrincipal UserPrincipal user) {

        List<DocumentNoteDto> notes = documentService.deleteNote(id, noteId, user);
        return ResponseEntity.ok(ApiResponseDto.success("Note deleted successfully", notes));
    }

    // Bookmarks APIs
    @GetMapping("/{id}/bookmarks")
    @Operation(summary = "Get document bookmarks", description = "Retrieve all bookmarks for this document")
    public ResponseEntity<ApiResponseDto<List<DocumentBookmarkDto>>> getBookmarks(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal user) {

        List<DocumentBookmarkDto> bookmarks = documentService.getBookmarks(id, user);
        return ResponseEntity.ok(ApiResponseDto.success("Bookmarks retrieved successfully", bookmarks));
    }

    @PostMapping("/{id}/bookmarks")
    @Operation(summary = "Add bookmark", description = "Add a new page bookmark to this document")
    public ResponseEntity<ApiResponseDto<List<DocumentBookmarkDto>>> addBookmark(
            @PathVariable Long id,
            @RequestBody DocumentBookmarkDto bookmarkDto,
            @AuthenticationPrincipal UserPrincipal user) {

        List<DocumentBookmarkDto> bookmarks = documentService.addBookmark(id, bookmarkDto, user);
        return ResponseEntity.ok(ApiResponseDto.success("Bookmark added successfully", bookmarks));
    }

    @DeleteMapping("/{id}/bookmarks/{bookmarkId}")
    @Operation(summary = "Delete bookmark", description = "Remove a bookmark from this document")
    public ResponseEntity<ApiResponseDto<List<DocumentBookmarkDto>>> deleteBookmark(
            @PathVariable Long id,
            @PathVariable String bookmarkId,
            @AuthenticationPrincipal UserPrincipal user) {

        List<DocumentBookmarkDto> bookmarks = documentService.deleteBookmark(id, bookmarkId, user);
        return ResponseEntity.ok(ApiResponseDto.success("Bookmark deleted successfully", bookmarks));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a document", description = "Delete a document by its ID along with the stored file and chat history")
    public ResponseEntity<ApiResponseDto<Void>> deleteDocument(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal user) {

        documentService.deleteDocument(id, user);
        return ResponseEntity
                .ok(ApiResponseDto.success("Document deleted successfully"));
    }
}

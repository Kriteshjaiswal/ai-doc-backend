package com.aidocqa.controller;

import com.aidocqa.dto.ApiResponseDto;
import com.aidocqa.dto.DocumentResponseDto;
import com.aidocqa.entity.User;
import com.aidocqa.service.DocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
@Tag(name = "Document Management", description = "APIs for uploading, listing, and deleting PDF documents")
public class DocumentController {

    private final DocumentService documentService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload a PDF document", description = "Upload a PDF file to extract text and store metadata")
    public ResponseEntity<ApiResponseDto<DocumentResponseDto>> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal User user) {

        DocumentResponseDto response = documentService.uploadDocument(file, user);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponseDto.success("Document uploaded successfully", response));
    }

    @GetMapping
    @Operation(summary = "List all documents", description = "Retrieve a list of all uploaded documents for the authenticated user")
    public ResponseEntity<ApiResponseDto<List<DocumentResponseDto>>> getAllDocuments(
            @AuthenticationPrincipal User user) {

        List<DocumentResponseDto> documents = documentService.getAllDocuments(user);
        return ResponseEntity
                .ok(ApiResponseDto.success("Documents retrieved successfully", documents));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a document", description = "Delete a document by its ID along with the stored file")
    public ResponseEntity<ApiResponseDto<Void>> deleteDocument(
            @PathVariable Long id,
            @AuthenticationPrincipal User user) {

        documentService.deleteDocument(id, user);
        return ResponseEntity
                .ok(ApiResponseDto.success("Document deleted successfully"));
    }
}

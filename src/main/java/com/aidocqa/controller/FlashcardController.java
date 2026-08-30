package com.aidocqa.controller;

import com.aidocqa.dto.ApiResponseDto;
import com.aidocqa.dto.FlashcardResponseDto;
import com.aidocqa.dto.FlashcardStatusUpdateRequestDto;
import com.aidocqa.security.UserPrincipal;
import com.aidocqa.service.FlashcardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/flashcards")
@RequiredArgsConstructor
@Tag(name = "Flashcard Management", description = "APIs for dynamic AI flashcard generation and progress tracking")
public class FlashcardController {

    private final FlashcardService flashcardService;

    @GetMapping
    @Operation(summary = "Get flashcards", description = "Retrieve all flashcards for authenticated user, optionally filtered by documentId")
    public ResponseEntity<ApiResponseDto<List<FlashcardResponseDto>>> getFlashcards(
            @RequestParam(value = "documentId", required = false) Long documentId,
            @AuthenticationPrincipal UserPrincipal user) {

        List<FlashcardResponseDto> response = flashcardService.getFlashcards(user, documentId);
        return ResponseEntity.ok(ApiResponseDto.success("Flashcards retrieved successfully", response));
    }

    @PostMapping("/generate")
    @Operation(summary = "Generate AI flashcards", description = "Generate flashcards from document extracted text using AI")
    public ResponseEntity<ApiResponseDto<List<FlashcardResponseDto>>> generateFlashcards(
            @RequestParam("documentId") Long documentId,
            @RequestParam(value = "count", defaultValue = "5") int count,
            @AuthenticationPrincipal UserPrincipal user) {

        List<FlashcardResponseDto> response = flashcardService.generateFlashcards(documentId, count, user);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponseDto.success("Flashcards generated successfully", response));
    }

    @PutMapping("/{id}/status")
    @Operation(summary = "Update flashcard status", description = "Update card status (mastered, need_revision, unseen) or favorite bookmark")
    public ResponseEntity<ApiResponseDto<FlashcardResponseDto>> updateFlashcardStatus(
            @PathVariable Long id,
            @RequestBody FlashcardStatusUpdateRequestDto dto,
            @AuthenticationPrincipal UserPrincipal user) {

        FlashcardResponseDto response = flashcardService.updateFlashcardStatus(id, dto, user);
        return ResponseEntity.ok(ApiResponseDto.success("Flashcard status updated", response));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete flashcard", description = "Delete a flashcard by ID")
    public ResponseEntity<ApiResponseDto<Void>> deleteFlashcard(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal user) {

        flashcardService.deleteFlashcard(id, user);
        return ResponseEntity.ok(ApiResponseDto.success("Flashcard deleted successfully"));
    }
}

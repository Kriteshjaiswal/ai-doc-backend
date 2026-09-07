package com.aidocqa;

import com.aidocqa.dto.GeminiResponseDto;
import com.aidocqa.service.GeminiApiService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class GeminiApiServiceTest {

    @Autowired
    private GeminiApiService geminiApiService;

    @Test
    public void testDesignPatternSummary() {
        String documentText = """
                Design Patterns: Elements of Reusable Object-Oriented Software.
                This book describes 23 object-oriented design patterns created by the Gang of Four (GoF).
                The patterns are categorized into Creational, Structural, and Behavioral categories.
                It features a comprehensive Lexi document-editor case study demonstrating pattern applications.
                """;

        String question = "Summarize Design Pattern in 2-3 sentences";

        GeminiResponseDto response = geminiApiService.generateAnswer(documentText, question);

        System.out.println("=== REAL RUNTIME TEST RESULT ===");
        System.out.println("Provider: " + response.getProvider());
        System.out.println("Model: " + response.getModel());
        System.out.println("Success: " + response.isSuccess());
        System.out.println("Grounded: " + response.isGrounded());
        System.out.println("Answer:\n" + response.getAnswer());

        assertNotNull(response);
        assertTrue(response.isSuccess());
        assertEquals("GEMINI", response.getProvider());
        assertTrue(response.isGrounded());
        assertNotNull(response.getAnswer());
    }

    @Test
    public void testStreamingAnswerMultimodal() {
        String documentText = "Spring Boot 3.3 includes enhanced Docker Compose and GraalVM native image support.";
        String question = "What does Spring Boot 3.3 include?";

        StringBuilder streamedChunks = new StringBuilder();
        GeminiResponseDto response = geminiApiService.streamAnswerMultimodal(
                documentText, null, question, null, streamedChunks::append
        );

        assertNotNull(response);
        assertTrue(response.isSuccess());
        assertEquals("GEMINI", response.getProvider());
        assertFalse(streamedChunks.isEmpty(), "Streamed output should not be empty");
        System.out.println("=== DYNAMIC STREAM TEST RESULT ===");
        System.out.println("Model: " + response.getModel());
        System.out.println("Streamed Text: " + streamedChunks);
    }

    @Test
    public void testLowDepthResponse() {
        String question = "Explain what a Distributed Job Scheduler is.";
        StringBuilder chunks = new StringBuilder();
        GeminiResponseDto response = geminiApiService.streamAnswerMultimodal(
                "req-low-test", "user@test.com", null, null, question, null, chunks::append, "LOW"
        );

        assertNotNull(response);
        assertTrue(response.isSuccess());
        System.out.println("=== LOW DEPTH RESULT ===");
        System.out.println("Model: " + response.getModel());
        System.out.println("Length: " + response.getAnswer().length());
        System.out.println("Answer:\n" + response.getAnswer());
        // LOW depth should be concise and fast
        assertTrue(response.getAnswer().length() < 2500, "LOW depth should be concise (< 2500 chars)");
    }

    @Test
    public void testMediumDepthResponse() {
        String question = "Explain how Cron scheduling works in distributed systems.";
        StringBuilder chunks = new StringBuilder();
        GeminiResponseDto response = geminiApiService.streamAnswerMultimodal(
                "req-med-test", "user@test.com", null, null, question, null, chunks::append, "MEDIUM"
        );

        assertNotNull(response);
        assertTrue(response.isSuccess());
        System.out.println("=== MEDIUM DEPTH RESULT ===");
        System.out.println("Model: " + response.getModel());
        System.out.println("Length: " + response.getAnswer().length());
        System.out.println("Answer:\n" + response.getAnswer());
        // MEDIUM depth should have balanced depth
        assertTrue(response.getAnswer().length() > 500, "MEDIUM depth should be substantial (> 500 chars)");
    }

    @Test
    public void testHighDepthDistributedJobScheduler() {
        String question = "Distributed job scheduler design karo jo millions of jobs execute kare. Cron scheduling, worker assignment, leader election, job locking, retry with exponential backoff, dead-letter queue, duplicate execution, worker crash aur exactly-once vs at-least-once execution ko explain karo.";
        StringBuilder chunks = new StringBuilder();
        GeminiResponseDto response = geminiApiService.streamAnswerMultimodal(
                "req-high-test", "user@test.com", null, null, question, null, chunks::append, "HIGH"
        );

        assertNotNull(response);
        assertTrue(response.isSuccess());
        System.out.println("=== HIGH DEPTH DISTRIBUTED SCHEDULER RESULT ===");
        System.out.println("Model: " + response.getModel());
        System.out.println("Length: " + response.getAnswer().length());
        System.out.println("Answer:\n" + response.getAnswer());
        assertTrue(response.getAnswer().length() > 2000, "HIGH depth answer should be comprehensive (> 2000 chars), got: " + response.getAnswer().length());
    }

    @Test
    public void testModelCooldownCircuitBreaker() {
        // Verify cooldown behavior
        assertFalse(geminiApiService.isModelInCooldown("gemini-test-model"));
        geminiApiService.markModelCooldown("gemini-test-model", 10, "Test Cooldown");
        assertTrue(geminiApiService.isModelInCooldown("gemini-test-model"));
    }
}

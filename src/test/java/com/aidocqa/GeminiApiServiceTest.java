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
}

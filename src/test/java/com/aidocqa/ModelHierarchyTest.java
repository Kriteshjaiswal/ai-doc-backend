package com.aidocqa;

import com.aidocqa.service.AdaptivePromptBuilder;
import com.aidocqa.service.DocumentAnalysisService;
import com.aidocqa.service.GeminiApiService;
import com.aidocqa.service.GeminiHandshakeService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ModelHierarchyTest {

    @Test
    public void testExactlyFiveFlashAndProModelsAcrossServices() {
        GeminiApiService apiService = new GeminiApiService(new RestTemplate(), new AdaptivePromptBuilder());
        GeminiHandshakeService handshakeService = new GeminiHandshakeService();

        // 1. Inspect GeminiApiService fallback models
        @SuppressWarnings("unchecked")
        List<String> apiFallbacks = (List<String>) ReflectionTestUtils.getField(GeminiApiService.class, "GEMINI_FALLBACK_MODELS");
        assertNotNull(apiFallbacks);
        assertEquals(4, apiFallbacks.size(), "Fallback models should be exactly 4 (+ 1 primary = 5 total models)");

        @SuppressWarnings("unchecked")
        List<String> allCandidates = (List<String>) ReflectionTestUtils.invokeMethod(apiService, "buildModelCandidateList");
        assertNotNull(allCandidates);
        assertEquals(5, allCandidates.size(), "Total candidate models must be exactly 5");

        for (String model : allCandidates) {
            assertFalse(model.toLowerCase().contains("lite"), "Model must not contain 'lite': " + model);
            assertTrue(model.toLowerCase().contains("flash") || model.toLowerCase().contains("pro"),
                    "Model must be Flash or Pro: " + model);
        }

        // 2. Inspect GeminiHandshakeService available models
        @SuppressWarnings("unchecked")
        List<String> handshakeModels = (List<String>) ReflectionTestUtils.getField(GeminiHandshakeService.class, "AVAILABLE_MODELS");
        assertNotNull(handshakeModels);
        assertEquals(5, handshakeModels.size(), "GeminiHandshakeService models must be exactly 5");

        for (String model : handshakeModels) {
            assertFalse(model.toLowerCase().contains("lite"), "Handshake model must not contain 'lite': " + model);
            assertTrue(model.toLowerCase().contains("flash") || model.toLowerCase().contains("pro"),
                    "Model must be Flash or Pro: " + model);
        }

        // 3. Inspect DocumentAnalysisService models
        @SuppressWarnings("unchecked")
        List<String> docAnalysisModels = (List<String>) ReflectionTestUtils.getField(DocumentAnalysisService.class, "GEMINI_MODELS");
        assertNotNull(docAnalysisModels);
        assertEquals(5, docAnalysisModels.size(), "DocumentAnalysisService models must be exactly 5");

        for (String model : docAnalysisModels) {
            assertFalse(model.toLowerCase().contains("lite"), "DocAnalysis model must not contain 'lite': " + model);
            assertTrue(model.toLowerCase().contains("flash") || model.toLowerCase().contains("pro"),
                    "Model must be Flash or Pro: " + model);
        }
    }
}

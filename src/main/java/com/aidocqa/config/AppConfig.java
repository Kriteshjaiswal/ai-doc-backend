package com.aidocqa.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Slf4j
@Configuration
public class AppConfig {

    @Value("${file.upload-dir}")
    private String uploadDir;

    @Value("${ai.model.timeout.connect-seconds:5}")
    private int connectTimeoutSeconds;

    @Value("${ai.model.timeout.read-seconds:60}")
    private int readTimeoutSeconds;

    @Bean
    public RestTemplate restTemplate() {
        org.springframework.http.client.SimpleClientHttpRequestFactory factory =
                new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) java.time.Duration.ofSeconds(connectTimeoutSeconds).toMillis());
        factory.setReadTimeout((int) java.time.Duration.ofSeconds(readTimeoutSeconds).toMillis());
        log.info("Initialized RestTemplate with fast AI fallback: connectTimeout={}s, readTimeout={}s",
                connectTimeoutSeconds, readTimeoutSeconds);
        return new RestTemplate(factory);
    }

    @PostConstruct
    public void initUploadDirectory() {
        try {
            Path uploadPath = Paths.get(uploadDir);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
                log.info("Created upload directory: {}", uploadPath.toAbsolutePath());
            }
        } catch (IOException e) {
            log.error("Failed to create upload directory: {}", e.getMessage());
            throw new RuntimeException("Could not create upload directory", e);
        }
    }
}

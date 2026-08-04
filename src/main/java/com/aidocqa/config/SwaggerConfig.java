package com.aidocqa.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("AI Document Q&A API")
                        .description("REST API for uploading PDF documents and asking AI-powered questions using Google Gemini")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("AI Document QA Team")
                                .email("support@aidocqa.com")));
    }
}

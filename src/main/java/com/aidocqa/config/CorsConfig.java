package com.aidocqa.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS is now managed by SecurityConfig's CorsConfigurationSource bean.
 * This class is kept as a placeholder for any future WebMvc customizations.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {
    // CORS configuration has been moved to SecurityConfig
}

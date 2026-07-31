package com.collectormarket.backend.api.config;

import java.util.List;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS for the separate frontend origin (OQ-4). Origins come from {@code app.cors.allowed-origins}
 * and are validated at startup to never be a wildcard (§4 security: no {@code *} on a public API).
 * The read API is GET-only, so only GET (plus preflight OPTIONS) is allowed.
 */
@Configuration
@EnableConfigurationProperties(CorsProperties.class)
public class WebConfig implements WebMvcConfigurer {

    private final List<String> allowedOrigins;

    public WebConfig(CorsProperties corsProperties) {
        List<String> origins = corsProperties.allowedOrigins();
        if (origins == null || origins.isEmpty()) {
            throw new IllegalStateException("app.cors.allowed-origins must be configured");
        }
        if (origins.stream().anyMatch(o -> o.contains("*"))) {
            throw new IllegalStateException(
                    "app.cors.allowed-origins must not contain a wildcard '*' (§4 security)");
        }
        this.allowedOrigins = origins;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins.toArray(String[]::new))
                .allowedMethods("GET", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(3600);
    }
}

package com.collectormarket.backend.api.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * OQ-4: the React frontend is a separate origin. Allowed origins are explicit and never wildcarded
 * (enforced in {@link WebConfig}) so credentials-bearing requests stay safe.
 */
@ConfigurationProperties(prefix = "app.cors")
public record CorsProperties(List<String> allowedOrigins) {
}

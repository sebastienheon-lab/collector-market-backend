package com.collectormarket.backend.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;

/**
 * OpenAPI document metadata (§8, M6 DoD). springdoc auto-scans the controllers; this only supplies
 * the info block. The spec is served at {@code /v3/api-docs} (always on) and rendered by Swagger UI
 * at {@code /swagger-ui.html} in non-prod profiles - see application.yaml / application-prod.yaml.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI collectorMarketOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Collector Market API")
                .version("v1")
                .description("Phase 1 read API: card search, current market view, price history "
                        + "(separate sales and floor series), grade breakdown."));
    }
}

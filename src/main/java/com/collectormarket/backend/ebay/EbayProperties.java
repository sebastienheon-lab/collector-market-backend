package com.collectormarket.backend.ebay;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * §5.1.1 / OQ-1 eBay Browse API configuration. client-id/client-secret come from the gitignored
 * credentials profile; oauth-token-url/api-base-url are environment-specific (sandbox vs prod).
 */
@ConfigurationProperties(prefix = "ebay")
public record EbayProperties(
        String clientId,
        String clientSecret,
        String oauthTokenUrl,
        String apiBaseUrl,
        int dailyCallLimit,
        long connectTimeoutMs,
        long responseTimeoutMs,
        Retry retry,
        Category category) {

    public record Retry(int maxAttempts, long minBackoffMs, long maxBackoffMs) {
    }

    public record Category(String tradingCardSingles) {
    }
}

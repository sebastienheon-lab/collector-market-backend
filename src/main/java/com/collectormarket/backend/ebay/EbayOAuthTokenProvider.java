package com.collectormarket.backend.ebay;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import com.collectormarket.backend.ebay.internal.EbayTokenResponse;

import reactor.core.publisher.Mono;

/**
 * OAuth 2.0 client-credentials token provider for the eBay Browse API (§5.1.1). Caches the
 * access token in a single {@link AtomicReference}, refreshing 5 minutes before actual expiry
 * so a token never expires mid-request.
 */
@Component
public class EbayOAuthTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(EbayOAuthTokenProvider.class);
    private static final Duration REFRESH_BUFFER = Duration.ofMinutes(5);
    private static final String SCOPE = "https://api.ebay.com/oauth/api_scope";

    private final WebClient tokenWebClient;
    private final EbayProperties properties;
    private final AtomicReference<CachedToken> cache = new AtomicReference<>();

    public EbayOAuthTokenProvider(WebClient.Builder webClientBuilder, EbayProperties properties) {
        this.properties = properties;
        this.tokenWebClient = webClientBuilder.build();
    }

    public Mono<String> getAccessToken() {
        CachedToken cached = cache.get();
        if (cached != null && cached.isUsable(Instant.now(), REFRESH_BUFFER)) {
            return Mono.just(cached.accessToken());
        }
        return fetchNewToken();
    }

    private Mono<String> fetchNewToken() {
        String basicAuth = Base64.getEncoder().encodeToString(
                (properties.clientId() + ":" + properties.clientSecret()).getBytes(StandardCharsets.UTF_8));

        return tokenWebClient.post()
                .uri(properties.oauthTokenUrl())
                .header(HttpHeaders.AUTHORIZATION, "Basic " + basicAuth)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                .bodyValue("grant_type=client_credentials&scope="
                        + URLEncoder.encode(SCOPE, StandardCharsets.UTF_8))
                .retrieve()
                .bodyToMono(EbayTokenResponse.class)
                .map(response -> {
                    CachedToken token = new CachedToken(
                            response.accessToken(), Instant.now().plusSeconds(response.expiresIn()));
                    cache.set(token);
                    log.info("ebay_oauth_token_refreshed expiresInSeconds={}", response.expiresIn());
                    return token.accessToken();
                });
    }
}

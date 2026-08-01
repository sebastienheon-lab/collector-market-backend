package com.collectormarket.backend.ebay;

import com.collectormarket.backend.properties.EbayProperties;

import java.time.Duration;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;

import com.collectormarket.backend.dto.ItemDetail;
import com.collectormarket.backend.dto.ItemSearchResult;
import com.collectormarket.backend.dto.SearchFilters;
import com.collectormarket.backend.ebay.internal.EbayItemDetailRaw;
import com.collectormarket.backend.ebay.internal.EbaySearchResponse;
import com.collectormarket.backend.observability.EbayCallState;

import io.micrometer.core.instrument.MeterRegistry;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

@Component
public class EbayBrowseClientImpl implements EbayBrowseClient {

    private static final Logger log = LoggerFactory.getLogger(EbayBrowseClientImpl.class);
    private static final String SEARCH_API = "browse.search";
    private static final String GET_ITEM_API = "browse.getItem";
    private static final String MARKETPLACE_ID = "EBAY_US";

    private final WebClient webClient;
    private final EbayOAuthTokenProvider tokenProvider;
    private final ApiCallCounter callCounter;
    private final EbayProperties properties;
    private final MeterRegistry meterRegistry;
    private final EbayCallState callState;

    public EbayBrowseClientImpl(WebClient ebayWebClient, EbayOAuthTokenProvider tokenProvider,
            ApiCallCounter callCounter, EbayProperties properties,
            MeterRegistry meterRegistry, EbayCallState callState) {
        this.webClient = ebayWebClient;
        this.tokenProvider = tokenProvider;
        this.callCounter = callCounter;
        this.properties = properties;
        this.meterRegistry = meterRegistry;
        this.callState = callState;
    }

    @Override
    public Mono<ItemSearchResult> searchItems(String query, String categoryId, SearchFilters filters) {
        SearchFilters effective = filters != null ? filters : SearchFilters.defaults();
        return call(SEARCH_API, token -> webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/buy/browse/v1/item_summary/search")
                        .queryParam("q", query)
                        .queryParam("category_ids", categoryId)
                        .queryParam("limit", effective.limit() != null ? effective.limit() : 50)
                        .queryParam("offset", effective.offset() != null ? effective.offset() : 0)
                        .build())
                .headers(headers -> applyAuth(headers, token))
                .retrieve()
                .onStatus(this::isTransient, this::transientError)
                .onStatus(HttpStatusCode::isError, this::nonTransientError)
                .bodyToMono(EbaySearchResponse.class)
                .map(EbayItemMapper::toItemSearchResult));
    }

    @Override
    public Mono<ItemDetail> getItem(String itemId) {
        return call(GET_ITEM_API, token -> webClient.get()
                .uri("/buy/browse/v1/item/{itemId}", itemId)
                .headers(headers -> applyAuth(headers, token))
                .retrieve()
                .onStatus(this::isTransient, this::transientError)
                .onStatus(HttpStatusCode::isError, this::nonTransientError)
                .bodyToMono(EbayItemDetailRaw.class)
                .map(EbayItemMapper::toItemDetail));
    }

    /**
     * Reserves a daily-budget slot (blocking JDBC pre-check, off-loaded to the elastic
     * scheduler), then - only if reserved - fetches a token and issues the request with
     * exponential backoff on transient (429/5xx) errors. A slot that's never reserved never
     * touches the network, matching the "decrement only if the request never left" rule.
     */
    private <T> Mono<T> call(String apiName, Function<String, Mono<T>> request) {
        return Mono.fromCallable(() -> callCounter.reserveCallSlot(apiName, properties.dailyCallLimit()))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(reserved -> {
                    if (!reserved) {
                        log.warn("ebay_api_call_blocked api={} reason=daily_budget_exceeded", apiName);
                        countCall(apiName, "blocked");
                        return Mono.error(new EbayCallBudgetExceededException(
                                "Daily call budget exceeded for " + apiName));
                    }
                    long startedAt = System.nanoTime();
                    return tokenProvider.getAccessToken()
                            .flatMap(request)
                            .doOnSuccess(result -> {
                                callState.recordSuccess();
                                countCall(apiName, "success");
                                log.info("ebay_api_call api={} cost=1 outcome=success durationMs={}",
                                        apiName, elapsedMs(startedAt));
                            })
                            .doOnError(error -> log.warn(
                                    "ebay_api_call api={} cost=1 outcome=error durationMs={} error={}",
                                    apiName, elapsedMs(startedAt), error.toString()))
                            .retryWhen(Retry.backoff(properties.retry().maxAttempts(),
                                            Duration.ofMillis(properties.retry().minBackoffMs()))
                                    .maxBackoff(Duration.ofMillis(properties.retry().maxBackoffMs()))
                                    .filter(EbayTransientApiException.class::isInstance))
                            // After retries are exhausted, count the call's final outcome exactly
                            // once (the doOnError above logs per attempt; this counter is terminal).
                            .doOnError(error -> countCall(apiName, "error"));
                });
    }

    private void countCall(String apiName, String outcome) {
        meterRegistry.counter("ebay.calls.total", "endpoint", apiName, "outcome", outcome).increment();
    }

    private void applyAuth(HttpHeaders headers, String token) {
        headers.setBearerAuth(token);
        headers.set("X-EBAY-C-MARKETPLACE-ID", MARKETPLACE_ID);
    }

    private boolean isTransient(HttpStatusCode status) {
        return status.value() == 429 || status.is5xxServerError();
    }

    private Mono<? extends Throwable> transientError(ClientResponse response) {
        return response.bodyToMono(String.class)
                .defaultIfEmpty("")
                .map(body -> new EbayTransientApiException(
                        "eBay transient error " + response.statusCode() + ": " + body));
    }

    private Mono<? extends Throwable> nonTransientError(ClientResponse response) {
        return response.bodyToMono(String.class)
                .defaultIfEmpty("")
                .map(body -> new EbayApiException(
                        "eBay API error " + response.statusCode() + ": " + body));
    }

    private static long elapsedMs(long startedAtNanos) {
        return Duration.ofNanos(System.nanoTime() - startedAtNanos).toMillis();
    }
}

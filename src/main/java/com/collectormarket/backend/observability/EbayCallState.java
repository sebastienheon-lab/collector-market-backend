package com.collectormarket.backend.observability;

import java.time.Instant;

import org.springframework.stereotype.Component;

/**
 * In-memory record of eBay integration liveness, updated as calls happen and read by
 * {@link EbayHealthIndicator} and the {@code ebay.quota.remaining} metric. Holds no listing data -
 * only the timestamp of the last successful call, so the health endpoint can judge staleness
 * without ever touching the network.
 * <p>
 * {@link #referenceInstant()} returns the last success, or - before any call has succeeded - the
 * app start time, so a freshly booted app isn't reported stale until the staleness window actually
 * elapses with no successful call.
 */
@Component
public class EbayCallState {

    private final Instant startedAt = Instant.now();
    private volatile Instant lastSuccessfulCallAt;

    public void recordSuccess() {
        lastSuccessfulCallAt = Instant.now();
    }

    public Instant lastSuccessfulCallAt() {
        return lastSuccessfulCallAt;
    }

    /** Last success, or app-start if there hasn't been one yet - the baseline for staleness. */
    public Instant referenceInstant() {
        Instant last = lastSuccessfulCallAt;
        return last != null ? last : startedAt;
    }
}

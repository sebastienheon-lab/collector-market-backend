package com.collectormarket.backend.observability;

import java.time.Duration;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.health.contributor.Status;
import org.springframework.stereotype.Component;

import com.collectormarket.backend.ebay.ApiCallCounter;
import com.collectormarket.backend.ebay.EbayOAuthTokenProvider;
import com.collectormarket.backend.ebay.EbayProperties;

/**
 * Reports on the eBay integration from <em>cached state only</em> - last successful call, quota
 * consumption, and cached-token validity. It never issues a live eBay request: {@code /actuator/
 * health} is polled frequently and a live call here would burn the OQ-1 daily quota.
 * <p>
 * Status logic:
 * <ul>
 *   <li><b>DOWN</b> - a token refresh has failed (the integration is truly broken). Maps to 503.</li>
 *   <li><b>DEGRADED</b> - no successful call within {@code ebay.health.stale-after-hours} (measured
 *       from app start until the first success). The app still serves DB-backed reads, so this maps
 *       to HTTP 200 (see {@code management.endpoint.health.status} in application.yaml).</li>
 *   <li><b>UP</b> - a recent successful call.</li>
 * </ul>
 * The bean name {@code ebayHealthIndicator} makes this the {@code ebay} health component.
 */
@Component("ebayHealthIndicator")
public class EbayHealthIndicator implements HealthIndicator {

    /** Less severe than DOWN/OUT_OF_SERVICE; mapped to HTTP 200 in application.yaml. */
    static final Status DEGRADED = new Status("DEGRADED");

    private final EbayCallState callState;
    private final EbayOAuthTokenProvider tokenProvider;
    private final ApiCallCounter callCounter;
    private final EbayProperties properties;
    private final Duration staleAfter;

    public EbayHealthIndicator(
            EbayCallState callState,
            EbayOAuthTokenProvider tokenProvider,
            ApiCallCounter callCounter,
            EbayProperties properties,
            @Value("${ebay.health.stale-after-hours:6}") long staleAfterHours) {
        this.callState = callState;
        this.tokenProvider = tokenProvider;
        this.callCounter = callCounter;
        this.properties = properties;
        this.staleAfter = Duration.ofHours(staleAfterHours);
    }

    @Override
    public Health health() {
        int callsToday = callCounter.totalCountToday();
        int dailyLimit = properties.dailyCallLimit();
        Instant lastSuccess = callState.lastSuccessfulCallAt();

        Health.Builder builder = new Health.Builder()
                .withDetail("lastSuccessfulCallAt", lastSuccess != null ? lastSuccess.toString() : "never")
                .withDetail("callsToday", callsToday)
                .withDetail("dailyLimit", dailyLimit)
                .withDetail("quotaRemaining", dailyLimit - callsToday)
                .withDetail("tokenValid", tokenProvider.hasValidCachedToken());

        if (tokenProvider.lastRefreshFailed()) {
            return builder.status(Status.DOWN).withDetail("reason", "token_refresh_failed").build();
        }
        if (isStale()) {
            return builder.status(DEGRADED)
                    .withDetail("reason", "no_successful_call_within_" + staleAfter.toHours() + "h")
                    .build();
        }
        return builder.status(Status.UP).build();
    }

    private boolean isStale() {
        return callState.referenceInstant().isBefore(Instant.now().minus(staleAfter));
    }
}

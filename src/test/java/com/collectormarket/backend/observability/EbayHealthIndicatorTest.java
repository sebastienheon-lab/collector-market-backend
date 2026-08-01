package com.collectormarket.backend.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;

import com.collectormarket.backend.ebay.ApiCallCounter;
import com.collectormarket.backend.ebay.EbayOAuthTokenProvider;
import com.collectormarket.backend.properties.EbayProperties;

/**
 * The eBay health indicator reports purely from cached state - none of its collaborators is a
 * network client, so {@link EbayHealthIndicator#health()} provably cannot make a live eBay call
 * (which would burn the OQ-1 daily quota on every health poll).
 */
@ExtendWith(MockitoExtension.class)
class EbayHealthIndicatorTest {

    private static final int STALE_AFTER_HOURS = 6;

    @Mock
    private EbayCallState callState;
    @Mock
    private EbayOAuthTokenProvider tokenProvider;
    @Mock
    private ApiCallCounter callCounter;

    private EbayHealthIndicator indicator() {
        EbayProperties properties = new EbayProperties(
                "id", "secret", "https://token", "https://api",
                5000, 5000, 10000,
                new EbayProperties.Retry(3, 10, 100),
                new EbayProperties.Category("261328"));
        return new EbayHealthIndicator(callState, tokenProvider, callCounter, properties, STALE_AFTER_HOURS);
    }

    @Test
    void up_whenRecentSuccessfulCall() {
        Instant now = Instant.now();
        when(callState.referenceInstant()).thenReturn(now);
        when(callState.lastSuccessfulCallAt()).thenReturn(now);
        when(tokenProvider.lastRefreshFailed()).thenReturn(false);
        when(tokenProvider.hasValidCachedToken()).thenReturn(true);
        when(callCounter.totalCountToday()).thenReturn(100);

        Health health = indicator().health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("quotaRemaining", 4900);
        assertThat(health.getDetails()).containsEntry("tokenValid", true);
    }

    @Test
    void degraded_whenNoSuccessfulCallWithinStaleWindow() {
        // Reference point (app start, no success yet) older than the 6h staleness window.
        when(callState.referenceInstant()).thenReturn(Instant.now().minus(10, ChronoUnit.HOURS));
        when(callState.lastSuccessfulCallAt()).thenReturn(null);
        when(tokenProvider.lastRefreshFailed()).thenReturn(false);

        Health health = indicator().health();

        assertThat(health.getStatus()).isEqualTo(EbayHealthIndicator.DEGRADED);
        assertThat(health.getDetails()).containsEntry("lastSuccessfulCallAt", "never");
    }

    @Test
    void down_whenTokenRefreshFailed_evenIfNotStale() {
        Instant now = Instant.now();
        when(callState.lastSuccessfulCallAt()).thenReturn(now);
        when(tokenProvider.lastRefreshFailed()).thenReturn(true);

        Health health = indicator().health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("reason", "token_refresh_failed");
    }
}

package com.collectormarket.backend.configs;

import org.springframework.context.annotation.Configuration;

import com.collectormarket.backend.ebay.ApiCallCounter;
import com.collectormarket.backend.properties.EbayProperties;
import com.collectormarket.backend.ingestion.CardTier;
import com.collectormarket.backend.ingestion.CardTracker;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Registers the poll-time gauges (§9 M7):
 * <ul>
 *   <li>{@code ebay.quota.remaining} - daily call budget headroom (limit minus calls made today);</li>
 *   <li>{@code cards.tracked}, tagged {@code tier} - how many cards sit in each tracking tier.</li>
 * </ul>
 * Each gauge reads its value from the DB on scrape; at Actuator's default scrape cadence that's a
 * single indexed count per gauge, negligible next to the ingestion workload. Counters and timers
 * ({@code ebay.calls.total}, {@code job.duration}, {@code job.failures}) are recorded inline at
 * their call sites instead - see EbayBrowseClientImpl and {@link JobMetrics}.
 */
@Configuration
public class ObservabilityConfig {

    public ObservabilityConfig(
            MeterRegistry meterRegistry,
            ApiCallCounter apiCallCounter,
            EbayProperties ebayProperties,
            CardTracker cardTracker) {

        Gauge.builder("ebay.quota.remaining",
                        () -> ebayProperties.dailyCallLimit() - apiCallCounter.totalCountToday())
                .description("Remaining eBay daily call budget (limit minus calls made today)")
                .register(meterRegistry);

        for (CardTier tier : CardTier.values()) {
            Gauge.builder("cards.tracked", () -> cardTracker.countByTier(tier))
                    .tag("tier", tier.name())
                    .description("Cards currently tracked, by tier")
                    .register(meterRegistry);
        }
    }
}

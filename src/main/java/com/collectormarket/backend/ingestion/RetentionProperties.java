package com.collectormarket.backend.ingestion;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * §5.4 / OQ-9 retention windows. Deliberately application.yml (code-reviewed), not app_setting -
 * see planning.md §5.4/§9 for why retention is treated as compliance-adjacent rather than a
 * runtime-tunable operational knob.
 */
@ConfigurationProperties(prefix = "retention")
public record RetentionProperties(int listingObservationDays, int priceSnapshotLinkbackDays) {
}

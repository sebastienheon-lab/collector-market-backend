package com.collectormarket.backend.ebay.internal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** §5.1.1: the estimatedSoldQuantity field the inferred-sales pipeline watches for deltas (M5). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EbayEstimatedAvailabilityRaw(
        String estimatedAvailabilityStatus,
        Integer estimatedAvailableQuantity,
        Integer estimatedSoldQuantity) {
}

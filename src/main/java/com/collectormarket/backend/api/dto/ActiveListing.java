package com.collectormarket.backend.api.dto;

import java.math.BigDecimal;

/** A single live listing behind the current market view (§8.3). */
public record ActiveListing(
        BigDecimal askPrice,
        String listingFormat,
        String externalUrl) {
}

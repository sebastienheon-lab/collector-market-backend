package com.collectormarket.backend.dto;

import java.math.BigDecimal;

public record ItemSummary(
        String itemId,
        String title,
        BigDecimal price,
        String currency,
        String condition,
        String itemWebUrl,
        String sellerUsername,
        ListingFormat listingFormat) {
}

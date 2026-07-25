package com.collectormarket.backend.ebay.dto;

import java.math.BigDecimal;

public record ItemDetail(
        String itemId,
        String title,
        BigDecimal price,
        String currency,
        String condition,
        String itemWebUrl,
        String sellerUsername,
        ListingFormat listingFormat,
        Integer estimatedAvailableQuantity,
        Integer estimatedSoldQuantity) {
}

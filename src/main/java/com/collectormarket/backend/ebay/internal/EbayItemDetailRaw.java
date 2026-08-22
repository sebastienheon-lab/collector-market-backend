package com.collectormarket.backend.ebay.internal;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Raw shape of {@code GET /buy/browse/v1/item/{item_id}}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EbayItemDetailRaw(
        String itemId,
        String title,
        EbayPrice price,
        String condition,
        String itemWebUrl,
        EbaySeller seller,
        List<String> buyingOptions,
        List<EbayEstimatedAvailabilityRaw> estimatedAvailabilities,
        EbayImage image) {
}

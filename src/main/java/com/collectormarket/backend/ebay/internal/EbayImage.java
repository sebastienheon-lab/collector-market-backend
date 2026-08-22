package com.collectormarket.backend.ebay.internal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Raw shape of the {@code image} object on {@code GET /buy/browse/v1/item/{item_id}}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EbayImage(String imageUrl) {
}

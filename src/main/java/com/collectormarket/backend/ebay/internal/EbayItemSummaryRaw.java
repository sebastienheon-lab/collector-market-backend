package com.collectormarket.backend.ebay.internal;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Raw shape of one element of {@code item_summary/search}'s {@code itemSummaries[]}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EbayItemSummaryRaw(
        String itemId,
        String title,
        EbayPrice price,
        String condition,
        String itemWebUrl,
        EbaySeller seller,
        List<String> buyingOptions) {
}

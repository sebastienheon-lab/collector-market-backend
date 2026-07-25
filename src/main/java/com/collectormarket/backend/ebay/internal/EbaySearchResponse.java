package com.collectormarket.backend.ebay.internal;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Raw shape of {@code GET /buy/browse/v1/item_summary/search}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EbaySearchResponse(
        List<EbayItemSummaryRaw> itemSummaries,
        Long total,
        Integer limit,
        Integer offset,
        String href,
        String next) {
}

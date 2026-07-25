package com.collectormarket.backend.ebay.dto;

import java.util.List;

public record ItemSearchResult(
        List<ItemSummary> items,
        long total,
        int limit,
        int offset) {
}

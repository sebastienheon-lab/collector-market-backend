package com.collectormarket.backend.api.dto;

import java.util.List;

/** Offset-paginated card search results (§8.1). */
public record SearchResponse(
        List<SearchResultItem> results,
        long totalCount,
        int page,
        int size) {
}

package com.collectormarket.backend.dto;

/** Minimal pagination controls for {@code searchItems}. Grows as ingestion (M5) needs more. */
public record SearchFilters(Integer limit, Integer offset) {

    public static SearchFilters defaults() {
        return new SearchFilters(50, 0);
    }
}

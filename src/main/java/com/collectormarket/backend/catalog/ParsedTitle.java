package com.collectormarket.backend.catalog;

/** Fields extracted from a raw eBay listing title (§10). Any field may be null/false if not found. */
public record ParsedTitle(
        Integer year,
        String playerName,
        String brand,
        String setName,
        String cardNumber,
        String gradeSource,
        String gradeValue,
        boolean variantDetected) {
}

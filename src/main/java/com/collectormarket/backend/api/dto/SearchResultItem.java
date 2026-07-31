package com.collectormarket.backend.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** One row in {@link SearchResponse} (§8.1). {@code sport} is the human-readable {@code sport.code}. */
public record SearchResultItem(
        UUID id,
        String playerName,
        int year,
        String setName,
        String cardNumber,
        String sport,
        boolean isRookie,
        BigDecimal latestSalePrice,
        LocalDate latestSaleDate) {
}

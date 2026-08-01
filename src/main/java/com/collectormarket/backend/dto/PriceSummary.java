package com.collectormarket.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Summary stats over the requested window (§8.2, F-09). Sale-derived stats are computed from the
 * {@code sales} series (SOLD + INFERRED_SALE); {@code currentFloorPrice} comes from the latest
 * {@code market_metric_daily} floor. All fields are null/zero when there is no data in the window.
 */
public record PriceSummary(
        BigDecimal lastSalePrice,
        LocalDate lastSaleDate,
        BigDecimal averagePrice,
        BigDecimal highPrice,
        BigDecimal lowPrice,
        long salesCount,
        BigDecimal currentFloorPrice) {
}

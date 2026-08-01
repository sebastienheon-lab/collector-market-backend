package com.collectormarket.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One day of the floor/ask series (§8.2), read straight from {@code market_metric_daily} - no
 * additional bucketing. This is the ASK-derived series and is kept strictly separate from
 * {@link Sale} transactions (F-07).
 */
public record FloorPoint(
        LocalDate date,
        BigDecimal floorPrice,
        BigDecimal medianAsk,
        int activeListings) {
}

package com.collectormarket.backend.dto;

import java.math.BigDecimal;

/** Average sale price and count for one grade level over the window (§8.5). */
public record GradeAverage(
        String grade,
        BigDecimal averagePrice,
        long salesCount) {
}

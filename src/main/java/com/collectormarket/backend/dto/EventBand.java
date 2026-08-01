package com.collectormarket.backend.dto;

import java.time.LocalDate;

/**
 * A competition date-range band overlaid on the price chart (§7.8-7.9, §8.2). Delivered inside the
 * price-history response - there is no separate events endpoint. {@code stage} may be null.
 */
public record EventBand(
        String competition,
        String label,
        String stage,
        LocalDate startDate,
        LocalDate endDate) {
}

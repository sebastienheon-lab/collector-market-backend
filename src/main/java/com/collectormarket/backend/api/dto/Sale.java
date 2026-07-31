package com.collectormarket.backend.api.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One transaction point in the price-history {@code sales} series (§8.2).
 * <p>
 * F-07: {@code priceType} is only ever {@code SOLD} or {@code INFERRED_SALE}. ASK/floor data
 * lives exclusively in {@link FloorPoint} and never appears here. {@code externalUrl} may be null
 * once the linkback field has been nulled by retention (§5.4).
 */
public record Sale(
        Instant soldAt,
        BigDecimal salePrice,
        String priceType,
        String source,
        String externalUrl) {
}

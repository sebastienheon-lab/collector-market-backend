package com.collectormarket.backend.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A related-card suggestion (F-11, OQ-5), populated only when the primary series is empty.
 * {@code relation} is {@code SAME_CARD_DIFFERENT_GRADE} or {@code SAME_SET_SAME_PLAYER}. For a
 * different-grade relation {@code grade} is set and {@code cardNumber} null; for a same-set
 * relation {@code cardNumber} is set and {@code grade} null. These are context, never presented
 * as this card's price estimate.
 */
public record RelatedCard(
        UUID cardId,
        String relation,
        String grade,
        String cardNumber,
        BigDecimal latestSalePrice,
        LocalDate latestSaleDate) {
}

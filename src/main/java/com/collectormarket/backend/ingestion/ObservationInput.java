package com.collectormarket.backend.ingestion;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One polled listing, ready to be written to {@code listing_observation} and checked for an
 * inferred sale. {@code quantitySold} is null for auctions (§5.1.1: no detail fetch for those).
 */
public record ObservationInput(
        UUID cardId,
        String externalListingId,
        Instant observedAt,
        BigDecimal askPrice,
        String listingFormat,
        Integer quantitySold,
        String gradeSource,
        String gradeValue,
        String externalUrl,
        String rawTitle) {
}

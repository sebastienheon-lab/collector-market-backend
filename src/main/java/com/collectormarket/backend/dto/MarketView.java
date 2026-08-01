package com.collectormarket.backend.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Current market state for a card+grade (§8.3): floor/median/active-count derived from the most
 * recent day of {@code listing_observation} rows, plus the individual live listings.
 */
public record MarketView(
        UUID cardId,
        String grade,
        BigDecimal floorPrice,
        BigDecimal medianAsk,
        int activeListings,
        List<ActiveListing> listings) {
}

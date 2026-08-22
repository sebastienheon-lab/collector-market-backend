package com.collectormarket.backend.repositories;

import java.math.BigDecimal;

/**
 * Native-query projection for {@link MarketRepository#currentListings}. Carries
 * {@code externalListingId} (the eBay itemId) so the service layer can fetch a live image for the
 * listing (OQ-16); that id is internal and never leaves as-is into {@link
 * com.collectormarket.backend.dto.ActiveListing}.
 */
public record ActiveListingRow(
        BigDecimal askPrice,
        String listingFormat,
        String externalUrl,
        String externalListingId) {
}

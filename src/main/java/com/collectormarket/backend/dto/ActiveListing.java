package com.collectormarket.backend.dto;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;

/** A single live listing behind the current market view (§8.3). */
public record ActiveListing(
        BigDecimal askPrice,
        String listingFormat,
        String externalUrl,
        @Schema(description = "Listing image, fetched live from the eBay Browse API for this "
                + "listing and never persisted (OQ-16). Null when the listing has no image.",
                nullable = true)
        String imageUrl) {
}

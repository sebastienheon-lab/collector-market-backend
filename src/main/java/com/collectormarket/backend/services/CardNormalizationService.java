package com.collectormarket.backend.services;

import java.util.Optional;
import java.util.UUID;

/** Turns a raw eBay listing title into a canonical card id (§10 Phase 1). */
public interface CardNormalizationService {

    /**
     * Resolves {@code rawTitle} to a catalog card id, or empty when it doesn't match (or is a
     * non-base variant) - in which case the title is logged to {@code unmatched_listing} for triage.
     */
    Optional<UUID> normalize(String rawTitle);
}

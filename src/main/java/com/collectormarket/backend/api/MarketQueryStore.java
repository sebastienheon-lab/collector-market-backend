package com.collectormarket.backend.api;

import com.collectormarket.backend.repositories.ActiveListingRow;
import com.collectormarket.backend.repositories.MarketRepository;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;

/**
 * Current market view (§8.3), read from the most recent day of {@code listing_observation} rows for
 * the card (+ optional grade) via {@link MarketRepository}. Per-listing rows only exist here (not in
 * {@code market_metric_daily}), and the 7-day retention window (§5.4) keeps this to genuinely
 * current data.
 */
@Component
public class MarketQueryStore {

    private final MarketRepository marketRepository;

    public MarketQueryStore(MarketRepository marketRepository) {
        this.marketRepository = marketRepository;
    }

    /** Live listings for the card+grade on its most recent observation day; empty if never observed. */
    public List<ActiveListingRow> currentListings(UUID cardId, Grade grade) {
        return marketRepository.currentListings(cardId, grade.gradeSource(), grade.gradeValue());
    }
}

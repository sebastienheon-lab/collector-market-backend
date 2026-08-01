package com.collectormarket.backend.services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.collectormarket.backend.api.Grade;
import com.collectormarket.backend.api.MarketQueryStore;
import com.collectormarket.backend.api.dto.ActiveListing;
import com.collectormarket.backend.api.dto.MarketView;

/**
 * Current market view (§8.3). Floor/median/active-count are computed in-process from the same live
 * listing set that is returned to the caller, so the aggregates and the listing list can never
 * disagree.
 */
@Service
public class MarketService {

    private final CardService cardService;
    private final MarketQueryStore marketQueryStore;

    public MarketService(CardService cardService, MarketQueryStore marketQueryStore) {
        this.cardService = cardService;
        this.marketQueryStore = marketQueryStore;
    }

    public MarketView market(UUID cardId, Grade grade) {
        cardService.requireExists(cardId);

        List<ActiveListing> listings = marketQueryStore.currentListings(cardId, grade).stream()
                .sorted(Comparator.comparing(ActiveListing::askPrice))
                .toList();

        BigDecimal floor = listings.isEmpty() ? null : listings.get(0).askPrice();
        BigDecimal median = median(listings);
        return new MarketView(cardId, grade.name(), floor, median, listings.size(), listings);
    }

    /** Median ask over the sorted listings; average of the two middles for an even count. */
    private BigDecimal median(List<ActiveListing> sortedByAsk) {
        int n = sortedByAsk.size();
        if (n == 0) {
            return null;
        }
        if (n % 2 == 1) {
            return sortedByAsk.get(n / 2).askPrice();
        }
        BigDecimal lo = sortedByAsk.get(n / 2 - 1).askPrice();
        BigDecimal hi = sortedByAsk.get(n / 2).askPrice();
        return lo.add(hi).divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
    }
}

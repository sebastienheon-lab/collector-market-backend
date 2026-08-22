package com.collectormarket.backend.services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.collectormarket.backend.api.Grade;
import com.collectormarket.backend.api.MarketQueryStore;
import com.collectormarket.backend.dto.ActiveListing;
import com.collectormarket.backend.dto.ItemDetail;
import com.collectormarket.backend.dto.MarketView;
import com.collectormarket.backend.ebay.EbayBrowseClient;
import com.collectormarket.backend.repositories.ActiveListingRow;

/**
 * Default {@link MarketService}. Floor/median/active-count are computed in-process from the same
 * live listing set that is returned to the caller, so the aggregates and the listing list can never
 * disagree.
 */
@Service
public class MarketServiceImpl implements MarketService {

    private static final Logger log = LoggerFactory.getLogger(MarketServiceImpl.class);

    private final CardService cardService;
    private final MarketQueryStore marketQueryStore;
    private final EbayBrowseClient ebayBrowseClient;

    public MarketServiceImpl(CardService cardService, MarketQueryStore marketQueryStore,
            EbayBrowseClient ebayBrowseClient) {
        this.cardService = cardService;
        this.marketQueryStore = marketQueryStore;
        this.ebayBrowseClient = ebayBrowseClient;
    }

    @Override
    public MarketView market(UUID cardId, Grade grade) {
        cardService.requireExists(cardId);

        List<ActiveListing> listings = marketQueryStore.currentListings(cardId, grade).stream()
                .sorted(Comparator.comparing(ActiveListingRow::askPrice))
                .map(row -> new ActiveListing(
                        row.askPrice(), row.listingFormat(), row.externalUrl(), fetchImageUrl(row)))
                .toList();

        BigDecimal floor = listings.isEmpty() ? null : listings.get(0).askPrice();
        BigDecimal median = median(listings);
        return new MarketView(cardId, grade.name(), floor, median, listings.size(), listings);
    }

    /**
     * Live pass-through only (OQ-16): fetched fresh from the Browse API for this response and
     * never persisted. A fetch failure just means no image for this listing, not a failed request.
     */
    private String fetchImageUrl(ActiveListingRow row) {
        try {
            ItemDetail detail = ebayBrowseClient.getItem(row.externalListingId()).block();
            return detail != null ? detail.imageUrl() : null;
        } catch (Exception e) {
            log.warn("market_image_fetch_failed itemId={} error={}", row.externalListingId(), e.toString());
            return null;
        }
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

package com.collectormarket.backend.services;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.collectormarket.backend.api.EventQueryStore;
import com.collectormarket.backend.api.Grade;
import com.collectormarket.backend.api.PriceQueryStore;
import com.collectormarket.backend.api.RelatedCardsQueryStore;
import com.collectormarket.backend.api.SaleCursor;
import com.collectormarket.backend.api.TimeRangeDays;
import com.collectormarket.backend.api.dto.EventBand;
import com.collectormarket.backend.api.dto.FloorPoint;
import com.collectormarket.backend.api.dto.PriceHistoryResponse;
import com.collectormarket.backend.api.dto.PriceSummary;
import com.collectormarket.backend.api.dto.RelatedCard;
import com.collectormarket.backend.api.dto.SalesPage;
import com.collectormarket.backend.ingestion.CardTracker;

/**
 * Default {@link PriceHistoryService} (§8.2). Assembles the two independent series - {@code sales}
 * (transactions) and {@code floorHistory} (ASK/floor) - kept strictly separate (F-07), plus event
 * bands and, only when both series are empty, related-card suggestions (F-11).
 * <p>
 * Also records demand: per the M5 decision, this endpoint is the click-through signal that touches
 * {@code card_tracking.last_engagement_at} via {@link CardTracker#recordEngagement} (a no-op unless
 * the card is already tracked).
 */
@Service
public class PriceHistoryServiceImpl implements PriceHistoryService {

    /** Max related-card suggestions returned in the empty state. */
    private static final int RELATED_LIMIT = 8;

    private final CardService cardService;
    private final CardTracker cardTracker;
    private final PriceQueryStore priceQueryStore;
    private final EventQueryStore eventQueryStore;
    private final RelatedCardsQueryStore relatedCardsQueryStore;

    public PriceHistoryServiceImpl(
            CardService cardService,
            CardTracker cardTracker,
            PriceQueryStore priceQueryStore,
            EventQueryStore eventQueryStore,
            RelatedCardsQueryStore relatedCardsQueryStore) {
        this.cardService = cardService;
        this.cardTracker = cardTracker;
        this.priceQueryStore = priceQueryStore;
        this.eventQueryStore = eventQueryStore;
        this.relatedCardsQueryStore = relatedCardsQueryStore;
    }

    @Override
    public PriceHistoryResponse prices(UUID cardId, Grade grade, int days, String cursorParam, int size) {
        int window = TimeRangeDays.validate(days);
        cardService.requireExists(cardId);

        // M5 demand signal - click-through on a card's price history.
        cardTracker.recordEngagement(cardId);

        SaleCursor cursor = cursorParam == null || cursorParam.isBlank() ? null : SaleCursor.decode(cursorParam);
        SalesPage sales = priceQueryStore.salesPage(cardId, grade, window, cursor, size);
        List<FloorPoint> floorHistory = priceQueryStore.floorHistory(cardId, grade, window);
        PriceSummary summary = priceQueryStore.summary(cardId, grade, window);
        List<EventBand> events = eventQueryStore.eventsForCard(cardId, window);

        // F-11 / §8.2: related cards populate ONLY when the primary series is empty. Guard on the
        // first page (cursor == null) - a follow-up page returning no rows doesn't mean "no data".
        List<RelatedCard> relatedCards = List.of();
        if (cursor == null && sales.items().isEmpty() && floorHistory.isEmpty()) {
            relatedCards = relatedCardsQueryStore.relatedCards(cardId, grade, RELATED_LIMIT);
        }

        return new PriceHistoryResponse(
                cardId, grade.name(), window, summary, sales, floorHistory, events, relatedCards);
    }
}

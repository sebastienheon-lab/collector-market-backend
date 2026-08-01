package com.collectormarket.backend.api;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.collectormarket.backend.api.dto.EventBand;

/**
 * Competition event bands for a card's price chart (§7.9 selection rule): events whose competition
 * belongs to the card's sport <em>or</em> the {@code multi} sport, intersected with the requested
 * time window. The window mirrors the price series - {@code [today - days, today]} - so every band
 * falls on the chart's historical axis (see the note in {@code PriceHistoryService}).
 */
@Component
public class EventQueryStore {

    private final EventBandRepository eventBandRepository;

    public EventQueryStore(EventBandRepository eventBandRepository) {
        this.eventBandRepository = eventBandRepository;
    }

    public List<EventBand> eventsForCard(UUID cardId, int days) {
        LocalDate today = LocalDate.now();
        return eventBandRepository.findEventBandsForCard(cardId, today.minusDays(days), today);
    }
}

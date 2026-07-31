package com.collectormarket.backend.api;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.collectormarket.backend.api.dto.GradeBreakdownResponse;

/** Grade premium spread (§8.5): average sale price per grade over the window. */
@Service
public class GradeBreakdownService {

    private final CardService cardService;
    private final PriceQueryStore priceQueryStore;

    public GradeBreakdownService(CardService cardService, PriceQueryStore priceQueryStore) {
        this.cardService = cardService;
        this.priceQueryStore = priceQueryStore;
    }

    public GradeBreakdownResponse breakdown(UUID cardId, int days) {
        int window = TimeRangeDays.validate(days);
        cardService.requireExists(cardId);
        return new GradeBreakdownResponse(cardId, window, priceQueryStore.gradeBreakdown(cardId, window));
    }
}

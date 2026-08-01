package com.collectormarket.backend.dto;

import java.util.List;
import java.util.UUID;

/**
 * Price history for a card+grade+window (§8.2). The transaction series ({@code sales}) and the
 * floor/ask series ({@code floorHistory}) are deliberately separate arrays and are never merged
 * (F-07). {@code relatedCards} is populated only when both series are empty (F-11).
 */
public record PriceHistoryResponse(
        UUID cardId,
        String grade,
        int timeRangeDays,
        PriceSummary summary,
        SalesPage sales,
        List<FloorPoint> floorHistory,
        List<EventBand> events,
        List<RelatedCard> relatedCards) {
}

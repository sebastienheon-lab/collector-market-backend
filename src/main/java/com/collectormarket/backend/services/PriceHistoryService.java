package com.collectormarket.backend.services;

import java.util.UUID;

import com.collectormarket.backend.api.Grade;
import com.collectormarket.backend.dto.PriceHistoryResponse;

/**
 * Price history (§8.2): the separate {@code sales} and {@code floorHistory} series (F-07), event
 * bands, and empty-state related cards (F-11). Also the M5 click-through demand signal.
 */
public interface PriceHistoryService {

    PriceHistoryResponse prices(UUID cardId, Grade grade, int days, String cursor, int size);
}

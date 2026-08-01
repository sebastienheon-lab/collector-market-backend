package com.collectormarket.backend.services;

import java.util.UUID;

import com.collectormarket.backend.api.dto.CardDetail;
import com.collectormarket.backend.api.error.CardNotFoundException;

/** Card-detail lookups (§8.4) and the shared existence guard used by the other endpoints. */
public interface CardService {

    /** Card catalog detail; throws {@link CardNotFoundException} (404) if no such card. */
    CardDetail getCard(UUID cardId);

    /** Throws {@link CardNotFoundException} (404) if no such card - the guard for market/prices/grades. */
    void requireExists(UUID cardId);
}

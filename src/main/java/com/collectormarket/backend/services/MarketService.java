package com.collectormarket.backend.services;

import java.util.UUID;

import com.collectormarket.backend.api.Grade;
import com.collectormarket.backend.api.dto.MarketView;

/** Current market view (§8.3): floor, median ask, active listing count, and the live listings. */
public interface MarketService {

    MarketView market(UUID cardId, Grade grade);
}

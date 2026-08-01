package com.collectormarket.backend.ebay;

import com.collectormarket.backend.dto.ItemDetail;
import com.collectormarket.backend.dto.ItemSearchResult;
import com.collectormarket.backend.dto.SearchFilters;

import reactor.core.publisher.Mono;

/**
 * eBay Browse API client (§5.1.1). Public surface only ever exposes app DTOs from
 * {@code com.collectormarket.backend.dto} - never eBay's raw JSON shape.
 */
public interface EbayBrowseClient {

    Mono<ItemSearchResult> searchItems(String query, String categoryId, SearchFilters filters);

    default Mono<ItemSearchResult> searchItems(String query, String categoryId) {
        return searchItems(query, categoryId, SearchFilters.defaults());
    }

    Mono<ItemDetail> getItem(String itemId);
}

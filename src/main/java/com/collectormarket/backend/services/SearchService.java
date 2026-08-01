package com.collectormarket.backend.services;

import com.collectormarket.backend.dto.SearchResponse;
import com.collectormarket.backend.exceptions.InvalidSportException;

/** Card search (§8.1): trigram ranking on player name, optional sport filter, offset pagination. */
public interface SearchService {

    /**
     * Searches cards by player name (trigram), optionally filtered by {@code sportCode} (a
     * human-readable {@code sport.code}); throws {@link InvalidSportException} for an unknown sport.
     */
    SearchResponse search(String query, String sportCode, int page, int size);
}

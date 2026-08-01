package com.collectormarket.backend.api;

import java.util.List;

import org.springframework.stereotype.Component;

import com.collectormarket.backend.api.SearchQueryParser.ParsedQuery;
import com.collectormarket.backend.api.dto.SearchResultItem;

/**
 * Postgres-native card search (§8.1), delegating to {@link SearchRepository}. Ranks by trigram
 * similarity on {@code player_name} when the query carries name text, additionally constraining on
 * year and card number when present; a query with no name text (e.g. only a year) skips ranking and
 * orders by year.
 */
@Component
public class SearchQueryStore {

    private final SearchRepository searchRepository;

    public SearchQueryStore(SearchRepository searchRepository) {
        this.searchRepository = searchRepository;
    }

    public List<SearchResultItem> search(ParsedQuery query, Short sportId, int limit, int offset) {
        if (query.playerTerm() != null) {
            return searchRepository.rankedSearch(
                    query.playerTerm(), query.year(), query.cardNumber(), sportId, limit, offset);
        }
        return searchRepository.unrankedSearch(query.year(), query.cardNumber(), sportId, limit, offset);
    }

    public long count(ParsedQuery query, Short sportId) {
        if (query.playerTerm() != null) {
            return searchRepository.countRanked(query.playerTerm(), query.year(), query.cardNumber(), sportId);
        }
        return searchRepository.countUnranked(query.year(), query.cardNumber(), sportId);
    }
}

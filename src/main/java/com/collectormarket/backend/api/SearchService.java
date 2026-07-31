package com.collectormarket.backend.api;

import java.util.List;

import org.springframework.stereotype.Service;

import com.collectormarket.backend.api.SearchQueryParser.ParsedQuery;
import com.collectormarket.backend.api.dto.SearchResponse;
import com.collectormarket.backend.api.dto.SearchResultItem;
import com.collectormarket.backend.api.error.InvalidSportException;

/** Card search (§8.1): trigram ranking on player name, optional sport filter, offset pagination. */
@Service
public class SearchService {

    private final SearchQueryStore searchQueryStore;
    private final CardQueryStore cardQueryStore;

    public SearchService(SearchQueryStore searchQueryStore, CardQueryStore cardQueryStore) {
        this.searchQueryStore = searchQueryStore;
        this.cardQueryStore = cardQueryStore;
    }

    public SearchResponse search(String query, String sportCode, int page, int size) {
        Short sportId = resolveSport(sportCode);
        ParsedQuery parsed = SearchQueryParser.parse(query);

        List<SearchResultItem> results = searchQueryStore.search(parsed, sportId, size, page * size);
        long total = searchQueryStore.count(parsed, sportId);
        return new SearchResponse(results, total, page, size);
    }

    private Short resolveSport(String sportCode) {
        if (sportCode == null || sportCode.isBlank()) {
            return null;
        }
        return cardQueryStore.findSportId(sportCode)
                .orElseThrow(() -> new InvalidSportException(sportCode));
    }
}

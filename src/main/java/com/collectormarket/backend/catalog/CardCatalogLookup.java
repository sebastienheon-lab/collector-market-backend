package com.collectormarket.backend.catalog;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.collectormarket.backend.repositories.CardRepository;

/**
 * Queries the {@code card} table directly (via {@link CardRepository}) rather than caching an
 * in-memory copy - simplest thing that works at seed scale (~160 rows); revisit if/when the catalog
 * grows enough for this to matter.
 */
@Component
public class CardCatalogLookup {

    private final CardRepository cardRepository;

    public CardCatalogLookup(CardRepository cardRepository) {
        this.cardRepository = cardRepository;
    }

    /** The "maintained player-name list" §10 calls for - derived from the catalog itself for MVP. */
    public Set<String> allPlayerNames() {
        return Set.copyOf(cardRepository.findDistinctPlayerNames());
    }

    /**
     * Exact match only (no fuzzy matching, per §10 Phase 1). card_number is used as a confirming
     * filter when both the title and the catalog row have one; an unconfirmed (null) catalog
     * card_number doesn't block a match on player+year+brand+set alone.
     */
    public Optional<UUID> findExactMatch(ParsedTitle parsed) {
        if (parsed.playerName() == null || parsed.year() == null || parsed.brand() == null) {
            return Optional.empty();
        }

        List<UUID> matches = cardRepository.findExactMatchIds(
                parsed.playerName(), toShort(parsed.year()), parsed.brand(),
                parsed.setName(), parsed.cardNumber());

        return matches.size() == 1 ? Optional.of(matches.get(0)) : Optional.empty();
    }

    private static Short toShort(Integer year) {
        return year != null ? year.shortValue() : null;
    }
}

package com.collectormarket.backend.catalog;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Queries the {@code card} table directly rather than caching an in-memory copy - simplest thing
 * that works at seed scale (~160 rows); revisit if/when the catalog grows enough for this to matter.
 */
@Component
public class CardCatalogLookup {

    private final JdbcTemplate jdbcTemplate;

    public CardCatalogLookup(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** The "maintained player-name list" §10 calls for - derived from the catalog itself for MVP. */
    public Set<String> allPlayerNames() {
        List<String> names = jdbcTemplate.queryForList("SELECT DISTINCT player_name FROM card", String.class);
        return Set.copyOf(names);
    }

    /**
     * Exact match only (no fuzzy matching, per §10 Phase 1). card_number is used as a
     * confirming filter when both the title and the catalog row have one; an unconfirmed
     * (null) catalog card_number doesn't block a match on player+year+brand+set alone.
     */
    public Optional<UUID> findExactMatch(ParsedTitle parsed) {
        if (parsed.playerName() == null || parsed.year() == null || parsed.brand() == null) {
            return Optional.empty();
        }

        List<UUID> matches = jdbcTemplate.query("""
                SELECT id FROM card
                WHERE player_name = ? AND year = ? AND brand = ?
                  AND (?::varchar IS NULL OR set_name = ?)
                  AND (?::varchar IS NULL OR card_number IS NULL OR card_number = ?)
                """,
                (rs, rowNum) -> (UUID) rs.getObject("id"),
                parsed.playerName(), parsed.year(), parsed.brand(),
                parsed.setName(), parsed.setName(),
                parsed.cardNumber(), parsed.cardNumber());

        return matches.size() == 1 ? Optional.of(matches.get(0)) : Optional.empty();
    }
}

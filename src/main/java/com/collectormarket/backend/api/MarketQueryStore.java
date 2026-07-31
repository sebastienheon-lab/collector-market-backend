package com.collectormarket.backend.api;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import com.collectormarket.backend.api.dto.ActiveListing;

/**
 * Current market view (§8.3), read from the most recent day of {@code listing_observation} rows for
 * the card (+ optional grade). Per-listing rows only exist here (not in {@code market_metric_daily}),
 * and the 7-day retention window (§5.4) keeps this to genuinely current data. A listing observed
 * more than once on that day collapses to its latest observation via {@code DISTINCT ON}.
 */
@Component
public class MarketQueryStore {

    private static final RowMapper<ActiveListing> LISTING_MAPPER = (rs, rowNum) -> new ActiveListing(
            rs.getBigDecimal("ask_price"),
            rs.getString("listing_format"),
            rs.getString("external_url"));

    private final JdbcTemplate jdbcTemplate;

    public MarketQueryStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Live listings for the card+grade on its most recent observation day; empty if never observed. */
    public List<ActiveListing> currentListings(UUID cardId, Grade grade) {
        StringBuilder sql = new StringBuilder();
        List<Object> args = new ArrayList<>();

        // Latest observation date for this card+grade.
        sql.append("""
                SELECT DISTINCT ON (external_listing_id)
                       ask_price, listing_format, external_url
                FROM listing_observation
                WHERE card_id = ?
                  AND observed_at::date = (
                      SELECT max(observed_at::date) FROM listing_observation
                      WHERE card_id = ?
                """);
        args.add(cardId);
        args.add(cardId);
        GradeFilter.append(sql, args, grade, null);
        sql.append("  )\n");
        GradeFilter.append(sql, args, grade, null);
        sql.append("ORDER BY external_listing_id, observed_at DESC");

        return jdbcTemplate.query(sql.toString(), LISTING_MAPPER, args.toArray());
    }
}

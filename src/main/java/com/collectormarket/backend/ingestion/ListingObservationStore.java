package com.collectormarket.backend.ingestion;

import static com.collectormarket.backend.ingestion.JdbcTimestamps.toTimestamp;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class ListingObservationStore {

    private final JdbcTemplate jdbcTemplate;

    public ListingObservationStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void insert(ObservationInput observation) {
        jdbcTemplate.update("""
                INSERT INTO listing_observation
                    (card_id, external_listing_id, observed_at, ask_price, listing_format, quantity_sold,
                     grade_source, grade_value, external_url, raw_title)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (external_listing_id, observed_at) DO NOTHING
                """,
                observation.cardId(), observation.externalListingId(), toTimestamp(observation.observedAt()),
                observation.askPrice(), observation.listingFormat(), observation.quantitySold(),
                observation.gradeSource(), observation.gradeValue(),
                observation.externalUrl(), observation.rawTitle());
    }
}

package com.collectormarket.backend.ingestion;

import static com.collectormarket.backend.ingestion.JdbcTimestamps.toTimestamp;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * §5.1.1 inferred-sale rule: a quantity_sold increase of N between consecutive fixed-price
 * observations of the same listing writes N {@code price_snapshot} rows at that observation's
 * ask price. A decrease, an unchanged value, or a listing's first-ever observation (nothing to
 * compare against) all mean "ignore" - including a listing that simply disappears, since that
 * never produces a new observation to trigger this detector in the first place.
 * <p>
 * Must run BEFORE the new observation is written to {@code listing_observation} - it looks up
 * "the latest existing row" as the prior observation, which only works while that row is still
 * the latest.
 */
@Component
public class InferredSaleDetector {

    private final JdbcTemplate jdbcTemplate;

    public InferredSaleDetector(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void detect(ObservationInput newObservation) {
        if (!"FIXED_PRICE".equals(newObservation.listingFormat()) || newObservation.quantitySold() == null) {
            return;
        }

        Integer priorQuantitySold = jdbcTemplate.query("""
                SELECT quantity_sold FROM listing_observation
                WHERE external_listing_id = ?
                ORDER BY observed_at DESC
                LIMIT 1
                """,
                rs -> rs.next() ? (Integer) rs.getObject("quantity_sold") : null,
                newObservation.externalListingId());

        if (priorQuantitySold == null) {
            return;
        }

        int delta = newObservation.quantitySold() - priorQuantitySold;
        if (delta <= 0) {
            return;
        }

        for (int unit = 1; unit <= delta; unit++) {
            String syntheticExternalId = newObservation.externalListingId() + "#" + (priorQuantitySold + unit);
            jdbcTemplate.update("""
                    INSERT INTO price_snapshot
                        (card_id, sale_price, sold_at, price_type, source, grade_source, grade_value,
                         platform, external_id, external_url, raw_title)
                    VALUES (?, ?, ?, 'INFERRED_SALE', 'EBAY_BROWSE_INFERRED', ?, ?, 'EBAY', ?, ?, ?)
                    """,
                    newObservation.cardId(), newObservation.askPrice(), toTimestamp(newObservation.observedAt()),
                    newObservation.gradeSource(), newObservation.gradeValue(),
                    syntheticExternalId, newObservation.externalUrl(), newObservation.rawTitle());
        }
    }
}

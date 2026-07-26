package com.collectormarket.backend.ingestion;

import java.time.LocalDate;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * §7.11: one {@code market_metric_daily} row per (card, grade, day), computed from that day's
 * {@code listing_observation} rows before the retention job deletes them.
 * <p>
 * Relies on an invariant upheld by whatever writes listing_observation (the Poller, via
 * GradeNormalizer): grade_source/grade_value are never truly NULL, defaulting to "RAW"/"RAW"
 * for ungraded listings. market_metric_daily's PK includes both columns, so a genuinely-NULL
 * grade on an observation row would fail to aggregate.
 * <p>
 * Only (card, grade) combinations with at least one observation on the target day get a row -
 * matching the spec ("one row per (card, grade, day) from that day's observations"). A
 * combination that had listings yesterday but zero today doesn't get a delistings-only row for
 * today; it simply stops appearing. Rows for (card, grade) pairs that DO have observations today
 * still correctly count any of yesterday's listings that vanished as delistings.
 */
@Component
public class DailyAggregator {

    private final JdbcTemplate jdbcTemplate;

    public DailyAggregator(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void aggregate(LocalDate date) {
        LocalDate previousDate = date.minusDays(1);

        jdbcTemplate.update("""
                WITH today AS (
                    SELECT card_id, grade_source, grade_value, external_listing_id, ask_price
                    FROM listing_observation
                    WHERE observed_at::date = ?
                ),
                yesterday AS (
                    SELECT card_id, grade_source, grade_value, external_listing_id
                    FROM listing_observation
                    WHERE observed_at::date = ?
                ),
                today_agg AS (
                    SELECT card_id, grade_source, grade_value,
                           MIN(ask_price) AS floor_price,
                           percentile_cont(0.5) WITHIN GROUP (ORDER BY ask_price) AS median_ask,
                           COUNT(DISTINCT external_listing_id) AS active_listings
                    FROM today
                    GROUP BY card_id, grade_source, grade_value
                ),
                new_listings_agg AS (
                    SELECT t.card_id, t.grade_source, t.grade_value,
                           COUNT(DISTINCT t.external_listing_id) AS new_listings
                    FROM today t
                    WHERE NOT EXISTS (
                        SELECT 1 FROM yesterday y WHERE y.external_listing_id = t.external_listing_id
                    )
                    GROUP BY t.card_id, t.grade_source, t.grade_value
                ),
                delistings_agg AS (
                    SELECT y.card_id, y.grade_source, y.grade_value,
                           COUNT(DISTINCT y.external_listing_id) AS delistings
                    FROM yesterday y
                    WHERE NOT EXISTS (
                        SELECT 1 FROM today t WHERE t.external_listing_id = y.external_listing_id
                    )
                    GROUP BY y.card_id, y.grade_source, y.grade_value
                )
                INSERT INTO market_metric_daily
                    (card_id, grade_source, grade_value, metric_date,
                     floor_price, median_ask, active_listings, new_listings, delistings)
                SELECT
                    a.card_id, a.grade_source, a.grade_value, ?,
                    a.floor_price, a.median_ask, a.active_listings,
                    COALESCE(n.new_listings, 0), COALESCE(d.delistings, 0)
                FROM today_agg a
                LEFT JOIN new_listings_agg n
                    ON n.card_id = a.card_id AND n.grade_source = a.grade_source AND n.grade_value = a.grade_value
                LEFT JOIN delistings_agg d
                    ON d.card_id = a.card_id AND d.grade_source = a.grade_source AND d.grade_value = a.grade_value
                ON CONFLICT (card_id, grade_source, grade_value, metric_date) DO UPDATE SET
                    floor_price = EXCLUDED.floor_price,
                    median_ask = EXCLUDED.median_ask,
                    active_listings = EXCLUDED.active_listings,
                    new_listings = EXCLUDED.new_listings,
                    delistings = EXCLUDED.delistings,
                    computed_at = now()
                """, date, previousDate, date);
    }
}

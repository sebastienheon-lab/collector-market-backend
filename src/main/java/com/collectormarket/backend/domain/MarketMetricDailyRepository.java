package com.collectormarket.backend.domain;

import java.time.LocalDate;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/**
 * Spring Data repository for {@link MarketMetricDaily}. The daily aggregation is a single
 * Postgres-native CTE upsert ({@code percentile_cont}, {@code ON CONFLICT DO UPDATE}) with no JPQL
 * equivalent - kept verbatim and executed through Hibernate.
 */
public interface MarketMetricDailyRepository
        extends JpaRepository<MarketMetricDaily, MarketMetricDailyId> {

    /**
     * §7.11: compute one row per (card, grade) for {@code metricDate} from that day's
     * {@code listing_observation} rows (floor = MIN ask, median = {@code percentile_cont}, counts of
     * new/delisted vs {@code previousDate}), upserting into {@code market_metric_daily}.
     */
    @Transactional
    @Modifying
    @Query(value = """
            WITH today AS (
                SELECT card_id, grade_source, grade_value, external_listing_id, ask_price
                FROM listing_observation
                WHERE observed_at::date = :metricDate
            ),
            yesterday AS (
                SELECT card_id, grade_source, grade_value, external_listing_id
                FROM listing_observation
                WHERE observed_at::date = :previousDate
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
                a.card_id, a.grade_source, a.grade_value, :metricDate,
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
            """, nativeQuery = true)
    void aggregate(@Param("metricDate") LocalDate metricDate, @Param("previousDate") LocalDate previousDate);
}

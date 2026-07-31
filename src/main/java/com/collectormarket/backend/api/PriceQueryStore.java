package com.collectormarket.backend.api;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import com.collectormarket.backend.api.dto.FloorPoint;
import com.collectormarket.backend.api.dto.GradeAverage;
import com.collectormarket.backend.api.dto.PriceSummary;
import com.collectormarket.backend.api.dto.Sale;
import com.collectormarket.backend.api.dto.SalesPage;

/**
 * Price-history reads (§8.2, §8.5). The transaction series comes from {@code price_snapshot}
 * (restricted to {@code SOLD}/{@code INFERRED_SALE} - never ASK, per F-07); the floor/ask series
 * comes from {@code market_metric_daily}. The two are produced by separate queries and never
 * merged.
 */
@Component
public class PriceQueryStore {

    /** SQL fragment: only transaction price types ever reach the sales series (F-07). */
    private static final String SALE_TYPES = "('SOLD','INFERRED_SALE')";

    private final JdbcTemplate jdbcTemplate;

    public PriceQueryStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // --- Sales (cursor-paginated) -------------------------------------------------------------

    private record SaleRow(UUID id, Sale sale) {
    }

    /**
     * One keyset page of sales, newest first. Fetches {@code limit + 1} rows to detect whether a
     * further page exists; the extra row's predecessor supplies {@code nextCursor}.
     */
    public SalesPage salesPage(UUID cardId, Grade grade, int days, SaleCursor cursor, int limit) {
        StringBuilder sql = new StringBuilder("""
                SELECT id, sold_at, sale_price, price_type, source, external_url
                FROM price_snapshot
                WHERE card_id = ?
                  AND price_type IN """ + SALE_TYPES + "\n"
                + "  AND sold_at >= now() - make_interval(days => ?)\n");
        List<Object> args = new ArrayList<>();
        args.add(cardId);
        args.add(days);
        GradeFilter.append(sql, args, grade, null);
        if (cursor != null) {
            sql.append("  AND (sold_at, id) < (?, ?)\n");
            args.add(Timestamp.from(cursor.soldAt()));
            args.add(cursor.id());
        }
        sql.append("ORDER BY sold_at DESC, id DESC\nLIMIT ?");
        args.add(limit + 1);

        RowMapper<SaleRow> mapper = (rs, rowNum) -> new SaleRow(
                rs.getObject("id", UUID.class),
                new Sale(
                        rs.getTimestamp("sold_at").toInstant(),
                        rs.getBigDecimal("sale_price"),
                        rs.getString("price_type"),
                        rs.getString("source"),
                        rs.getString("external_url")));

        List<SaleRow> rows = jdbcTemplate.query(sql.toString(), mapper, args.toArray());

        boolean hasMore = rows.size() > limit;
        List<SaleRow> pageRows = hasMore ? rows.subList(0, limit) : rows;
        List<Sale> items = pageRows.stream().map(SaleRow::sale).toList();

        String nextCursor = null;
        if (hasMore) {
            SaleRow last = pageRows.get(pageRows.size() - 1);
            nextCursor = new SaleCursor(last.sale().soldAt(), last.id()).encode();
        }
        return new SalesPage(items, nextCursor);
    }

    // --- Summary ------------------------------------------------------------------------------

    /** F-09 summary over the window: sale stats from {@code price_snapshot}, floor from aggregates. */
    public PriceSummary summary(UUID cardId, Grade grade, int days) {
        StringBuilder agg = new StringBuilder("""
                SELECT count(*) AS cnt, avg(sale_price) AS avg_price,
                       max(sale_price) AS high_price, min(sale_price) AS low_price
                FROM price_snapshot
                WHERE card_id = ?
                  AND price_type IN """ + SALE_TYPES + "\n"
                + "  AND sold_at >= now() - make_interval(days => ?)\n");
        List<Object> aggArgs = new ArrayList<>(List.of(cardId, days));
        GradeFilter.append(agg, aggArgs, grade, null);

        PriceSummaryAgg a = jdbcTemplate.queryForObject(agg.toString(), (rs, n) -> new PriceSummaryAgg(
                rs.getLong("cnt"),
                rs.getBigDecimal("avg_price"),
                rs.getBigDecimal("high_price"),
                rs.getBigDecimal("low_price")), aggArgs.toArray());

        // Latest sale within the window (price + date).
        StringBuilder latest = new StringBuilder("""
                SELECT sale_price, sold_at::date AS sold_date
                FROM price_snapshot
                WHERE card_id = ?
                  AND price_type IN """ + SALE_TYPES + "\n"
                + "  AND sold_at >= now() - make_interval(days => ?)\n");
        List<Object> latestArgs = new ArrayList<>(List.of(cardId, days));
        GradeFilter.append(latest, latestArgs, grade, null);
        latest.append("ORDER BY sold_at DESC LIMIT 1");

        LastSale last = jdbcTemplate.query(latest.toString(), (rs, n) ->
                new LastSale(rs.getBigDecimal("sale_price"), rs.getObject("sold_date", LocalDate.class)),
                latestArgs.toArray()).stream().findFirst().orElse(new LastSale(null, null));

        BigDecimal currentFloor = currentFloor(cardId, grade);

        return new PriceSummary(
                last.price(), last.date(),
                a.avgPrice() != null ? a.avgPrice().setScale(2, java.math.RoundingMode.HALF_UP) : null,
                a.highPrice(), a.lowPrice(), a.count(), currentFloor);
    }

    private record PriceSummaryAgg(long count, BigDecimal avgPrice, BigDecimal highPrice, BigDecimal lowPrice) {
    }

    private record LastSale(BigDecimal price, LocalDate date) {
    }

    // --- Floor history ------------------------------------------------------------------------

    private static final RowMapper<FloorPoint> FLOOR_MAPPER = (rs, rowNum) -> new FloorPoint(
            rs.getObject("metric_date", LocalDate.class),
            rs.getBigDecimal("floor_price"),
            rs.getBigDecimal("median_ask"),
            rs.getInt("active_listings"));

    /**
     * Daily floor series from {@code market_metric_daily} - no extra bucketing (one row per day).
     * For {@link Grade#ALL} the per-grade rows are folded per date: floor = MIN, active = SUM,
     * median = AVG of the per-grade medians (an approximation - a true cross-grade median isn't
     * recoverable from pre-aggregated rows).
     */
    public List<FloorPoint> floorHistory(UUID cardId, Grade grade, int days) {
        if (grade.filtersByGrade()) {
            StringBuilder sql = new StringBuilder("""
                    SELECT metric_date, floor_price, median_ask, active_listings
                    FROM market_metric_daily
                    WHERE card_id = ?
                      AND metric_date >= current_date - ?
                    """);
            List<Object> args = new ArrayList<>(List.of(cardId, days));
            GradeFilter.append(sql, args, grade, null);
            sql.append("ORDER BY metric_date ASC");
            return jdbcTemplate.query(sql.toString(), FLOOR_MAPPER, args.toArray());
        }
        return jdbcTemplate.query("""
                SELECT metric_date,
                       MIN(floor_price) AS floor_price,
                       ROUND(AVG(median_ask), 2) AS median_ask,
                       SUM(active_listings) AS active_listings
                FROM market_metric_daily
                WHERE card_id = ?
                  AND metric_date >= current_date - ?
                GROUP BY metric_date
                ORDER BY metric_date ASC
                """, FLOOR_MAPPER, cardId, days);
    }

    private BigDecimal currentFloor(UUID cardId, Grade grade) {
        if (grade.filtersByGrade()) {
            StringBuilder sql = new StringBuilder("""
                    SELECT floor_price
                    FROM market_metric_daily
                    WHERE card_id = ?
                    """);
            List<Object> args = new ArrayList<>(List.of(cardId));
            GradeFilter.append(sql, args, grade, null);
            sql.append("ORDER BY metric_date DESC LIMIT 1");
            List<BigDecimal> floors = jdbcTemplate.query(sql.toString(),
                    (rs, n) -> rs.getBigDecimal("floor_price"), args.toArray());
            return floors.isEmpty() ? null : floors.get(0);
        }
        // MIN over grades on the latest date; queryForObject tolerates the NULL an empty set yields.
        return jdbcTemplate.queryForObject("""
                SELECT MIN(floor_price)
                FROM market_metric_daily
                WHERE card_id = ?
                  AND metric_date = (SELECT max(metric_date) FROM market_metric_daily WHERE card_id = ?)
                """, BigDecimal.class, cardId, cardId);
    }

    // --- Grade breakdown (§8.5) ---------------------------------------------------------------

    public List<GradeAverage> gradeBreakdown(UUID cardId, int days) {
        return jdbcTemplate.query("""
                SELECT grade_source, grade_value,
                       ROUND(avg(sale_price), 2) AS avg_price, count(*) AS cnt
                FROM price_snapshot
                WHERE card_id = ?
                  AND price_type IN """ + SALE_TYPES + "\n"
                + "  AND sold_at >= now() - make_interval(days => ?)\n"
                + "GROUP BY grade_source, grade_value\n"
                + "ORDER BY avg_price DESC",
                (rs, n) -> new GradeAverage(
                        Grade.tokenFor(rs.getString("grade_source"), rs.getString("grade_value")),
                        rs.getBigDecimal("avg_price"),
                        rs.getLong("cnt")),
                cardId, days);
    }
}

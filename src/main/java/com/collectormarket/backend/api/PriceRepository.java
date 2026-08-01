package com.collectormarket.backend.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import com.collectormarket.backend.api.dto.FloorPoint;
import com.collectormarket.backend.domain.PriceSnapshot;

/**
 * Price-history reads (§8.2, §8.5). The transaction series comes from {@code price_snapshot}
 * (restricted to {@code SOLD}/{@code INFERRED_SALE} - never ASK, per F-07); the floor/ask series
 * comes from {@code market_metric_daily}. Keyset paging is split into a first-page and an
 * after-cursor method so the nullable cursor never has to be bound as a typed null. A null
 * {@code gradeSource} means the {@code ALL} token (no grade filter).
 */
public interface PriceRepository extends Repository<PriceSnapshot, UUID> {

    // --- projections -------------------------------------------------------------------------

    record SaleRow(UUID id, Instant soldAt, BigDecimal salePrice, String priceType,
                   String source, String externalUrl) {
    }

    record PriceSummaryAgg(long count, BigDecimal avgPrice, BigDecimal highPrice, BigDecimal lowPrice) {
    }

    record LastSale(BigDecimal salePrice, LocalDate soldDate) {
    }

    record GradeAverageRow(String gradeSource, String gradeValue, BigDecimal averagePrice, long salesCount) {
    }

    // --- sales (keyset paginated) ------------------------------------------------------------

    String SALES_SELECT = """
            SELECT id AS id, sold_at AS soldAt, sale_price AS salePrice, price_type AS priceType,
                   source AS source, external_url AS externalUrl
            FROM price_snapshot
            WHERE card_id = :cardId
              AND price_type IN ('SOLD','INFERRED_SALE')
              AND sold_at >= :fromTime
              AND (:gradeSource IS NULL OR (grade_source = :gradeSource AND grade_value = :gradeValue))
            """;

    @Query(value = SALES_SELECT + "ORDER BY sold_at DESC, id DESC LIMIT :limit", nativeQuery = true)
    List<SaleRow> firstSalesPage(
            @Param("cardId") UUID cardId, @Param("gradeSource") String gradeSource,
            @Param("gradeValue") String gradeValue, @Param("fromTime") Instant fromTime,
            @Param("limit") int limit);

    @Query(value = SALES_SELECT
            + "  AND (sold_at, id) < (:cursorSoldAt, :cursorId)\n"
            + "ORDER BY sold_at DESC, id DESC LIMIT :limit", nativeQuery = true)
    List<SaleRow> salesPageAfter(
            @Param("cardId") UUID cardId, @Param("gradeSource") String gradeSource,
            @Param("gradeValue") String gradeValue, @Param("fromTime") Instant fromTime,
            @Param("cursorSoldAt") Instant cursorSoldAt, @Param("cursorId") UUID cursorId,
            @Param("limit") int limit);

    // --- summary -----------------------------------------------------------------------------

    @Query(value = """
            SELECT count(*) AS count, avg(sale_price) AS avgPrice,
                   max(sale_price) AS highPrice, min(sale_price) AS lowPrice
            FROM price_snapshot
            WHERE card_id = :cardId
              AND price_type IN ('SOLD','INFERRED_SALE')
              AND sold_at >= :fromTime
              AND (:gradeSource IS NULL OR (grade_source = :gradeSource AND grade_value = :gradeValue))
            """, nativeQuery = true)
    PriceSummaryAgg summaryAgg(
            @Param("cardId") UUID cardId, @Param("gradeSource") String gradeSource,
            @Param("gradeValue") String gradeValue, @Param("fromTime") Instant fromTime);

    @Query(value = """
            SELECT sale_price AS salePrice, sold_at::date AS soldDate
            FROM price_snapshot
            WHERE card_id = :cardId
              AND price_type IN ('SOLD','INFERRED_SALE')
              AND sold_at >= :fromTime
              AND (:gradeSource IS NULL OR (grade_source = :gradeSource AND grade_value = :gradeValue))
            ORDER BY sold_at DESC LIMIT 1
            """, nativeQuery = true)
    List<LastSale> latestSale(
            @Param("cardId") UUID cardId, @Param("gradeSource") String gradeSource,
            @Param("gradeValue") String gradeValue, @Param("fromTime") Instant fromTime);

    // --- floor history (market_metric_daily) -------------------------------------------------

    @Query(value = """
            SELECT metric_date AS date, floor_price AS floorPrice, median_ask AS medianAsk,
                   active_listings AS activeListings
            FROM market_metric_daily
            WHERE card_id = :cardId AND metric_date >= :fromDate
              AND grade_source = :gradeSource AND grade_value = :gradeValue
            ORDER BY metric_date ASC
            """, nativeQuery = true)
    List<FloorPoint> floorHistoryForGrade(
            @Param("cardId") UUID cardId, @Param("gradeSource") String gradeSource,
            @Param("gradeValue") String gradeValue, @Param("fromDate") LocalDate fromDate);

    @Query(value = """
            SELECT metric_date AS date, MIN(floor_price) AS floorPrice,
                   ROUND(AVG(median_ask), 2) AS medianAsk, CAST(SUM(active_listings) AS int) AS activeListings
            FROM market_metric_daily
            WHERE card_id = :cardId AND metric_date >= :fromDate
            GROUP BY metric_date
            ORDER BY metric_date ASC
            """, nativeQuery = true)
    List<FloorPoint> floorHistoryAllGrades(
            @Param("cardId") UUID cardId, @Param("fromDate") LocalDate fromDate);

    @Query(value = """
            SELECT floor_price FROM market_metric_daily
            WHERE card_id = :cardId AND grade_source = :gradeSource AND grade_value = :gradeValue
            ORDER BY metric_date DESC LIMIT 1
            """, nativeQuery = true)
    BigDecimal currentFloorForGrade(
            @Param("cardId") UUID cardId, @Param("gradeSource") String gradeSource,
            @Param("gradeValue") String gradeValue);

    @Query(value = """
            SELECT MIN(floor_price) FROM market_metric_daily
            WHERE card_id = :cardId
              AND metric_date = (SELECT max(metric_date) FROM market_metric_daily WHERE card_id = :cardId)
            """, nativeQuery = true)
    BigDecimal currentFloorAllGrades(@Param("cardId") UUID cardId);

    // --- grade breakdown (§8.5) --------------------------------------------------------------

    @Query(value = """
            SELECT grade_source AS gradeSource, grade_value AS gradeValue,
                   ROUND(avg(sale_price), 2) AS averagePrice, count(*) AS salesCount
            FROM price_snapshot
            WHERE card_id = :cardId
              AND price_type IN ('SOLD','INFERRED_SALE')
              AND sold_at >= :fromTime
            GROUP BY grade_source, grade_value
            ORDER BY averagePrice DESC
            """, nativeQuery = true)
    List<GradeAverageRow> gradeBreakdown(@Param("cardId") UUID cardId, @Param("fromTime") Instant fromTime);
}

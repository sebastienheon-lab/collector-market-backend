package com.collectormarket.backend.api;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.collectormarket.backend.api.PriceRepository.LastSale;
import com.collectormarket.backend.api.PriceRepository.PriceSummaryAgg;
import com.collectormarket.backend.api.PriceRepository.SaleRow;
import com.collectormarket.backend.api.dto.FloorPoint;
import com.collectormarket.backend.api.dto.GradeAverage;
import com.collectormarket.backend.api.dto.PriceSummary;
import com.collectormarket.backend.api.dto.Sale;
import com.collectormarket.backend.api.dto.SalesPage;

/**
 * Price-history reads (§8.2, §8.5), orchestrating {@link PriceRepository}. The transaction series
 * ({@code sales}) and the floor/ask series ({@code floorHistory}) come from separate queries and are
 * never merged (F-07). Keyset cursor paging, the summary assembly, and the {@code ALL}-vs-specific
 * grade branching live here; the SQL lives in the repository.
 */
@Component
public class PriceQueryStore {

    private final PriceRepository priceRepository;

    public PriceQueryStore(PriceRepository priceRepository) {
        this.priceRepository = priceRepository;
    }

    // --- Sales (cursor-paginated) -------------------------------------------------------------

    /**
     * One keyset page of sales, newest first. Fetches {@code limit + 1} rows to detect whether a
     * further page exists; the last kept row supplies {@code nextCursor}.
     */
    public SalesPage salesPage(UUID cardId, Grade grade, int days, SaleCursor cursor, int limit) {
        Instant fromTime = daysAgo(days);
        List<SaleRow> rows = cursor == null
                ? priceRepository.firstSalesPage(cardId, grade.gradeSource(), grade.gradeValue(), fromTime, limit + 1)
                : priceRepository.salesPageAfter(cardId, grade.gradeSource(), grade.gradeValue(), fromTime,
                        cursor.soldAt(), cursor.id(), limit + 1);

        boolean hasMore = rows.size() > limit;
        List<SaleRow> pageRows = hasMore ? rows.subList(0, limit) : rows;
        List<Sale> items = pageRows.stream()
                .map(r -> new Sale(r.soldAt(), r.salePrice(), r.priceType(), r.source(), r.externalUrl()))
                .toList();

        String nextCursor = null;
        if (hasMore) {
            SaleRow last = pageRows.get(pageRows.size() - 1);
            nextCursor = new SaleCursor(last.soldAt(), last.id()).encode();
        }
        return new SalesPage(items, nextCursor);
    }

    // --- Summary ------------------------------------------------------------------------------

    /** F-09 summary over the window: sale stats from {@code price_snapshot}, floor from aggregates. */
    public PriceSummary summary(UUID cardId, Grade grade, int days) {
        Instant fromTime = daysAgo(days);
        PriceSummaryAgg agg = priceRepository.summaryAgg(
                cardId, grade.gradeSource(), grade.gradeValue(), fromTime);
        LastSale last = priceRepository.latestSale(
                cardId, grade.gradeSource(), grade.gradeValue(), fromTime).stream()
                .findFirst().orElse(new LastSale(null, null));
        BigDecimal currentFloor = grade.filtersByGrade()
                ? priceRepository.currentFloorForGrade(cardId, grade.gradeSource(), grade.gradeValue())
                : priceRepository.currentFloorAllGrades(cardId);

        return new PriceSummary(
                last.salePrice(), last.soldDate(),
                agg.avgPrice() != null ? agg.avgPrice().setScale(2, RoundingMode.HALF_UP) : null,
                agg.highPrice(), agg.lowPrice(), agg.count(), currentFloor);
    }

    // --- Floor history ------------------------------------------------------------------------

    /**
     * Daily floor series from {@code market_metric_daily} - no extra bucketing (one row per day).
     * For {@link Grade#ALL} the per-grade rows are folded per date: floor = MIN, active = SUM,
     * median = AVG of the per-grade medians (an approximation - a true cross-grade median isn't
     * recoverable from pre-aggregated rows).
     */
    public List<FloorPoint> floorHistory(UUID cardId, Grade grade, int days) {
        LocalDate fromDate = LocalDate.now().minusDays(days);
        return grade.filtersByGrade()
                ? priceRepository.floorHistoryForGrade(cardId, grade.gradeSource(), grade.gradeValue(), fromDate)
                : priceRepository.floorHistoryAllGrades(cardId, fromDate);
    }

    // --- Grade breakdown (§8.5) ---------------------------------------------------------------

    public List<GradeAverage> gradeBreakdown(UUID cardId, int days) {
        return priceRepository.gradeBreakdown(cardId, daysAgo(days)).stream()
                .map(r -> new GradeAverage(
                        Grade.tokenFor(r.gradeSource(), r.gradeValue()), r.averagePrice(), r.salesCount()))
                .toList();
    }

    private static Instant daysAgo(int days) {
        return Instant.now().minus(days, ChronoUnit.DAYS);
    }
}

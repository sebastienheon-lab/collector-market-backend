package com.collectormarket.backend.ingestion;

import java.time.LocalDate;

import org.springframework.stereotype.Component;

import com.collectormarket.backend.repositories.MarketMetricDailyRepository;

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

    private final MarketMetricDailyRepository marketMetricDailyRepository;

    public DailyAggregator(MarketMetricDailyRepository marketMetricDailyRepository) {
        this.marketMetricDailyRepository = marketMetricDailyRepository;
    }

    public void aggregate(LocalDate date) {
        marketMetricDailyRepository.aggregate(date, date.minusDays(1));
    }
}

package com.collectormarket.backend.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.collectormarket.backend.domain.MarketMetricDaily;
import com.collectormarket.backend.domain.MarketMetricDailyId;

class DailyAggregatorTest extends IngestionJpaTestBase {

    private static final LocalDate TODAY = LocalDate.of(2026, 6, 15);
    private static final LocalDate YESTERDAY = TODAY.minusDays(1);

    @Autowired
    private DailyAggregator aggregator;

    @Autowired
    private com.collectormarket.backend.repositories.MarketMetricDailyRepository marketMetricDailyRepository;

    @Test
    void computesFloorMedianActiveNewAndDelistings() {
        UUID cardId = saveTestCard();

        // Yesterday: A=100, B=120, C=110
        observe(cardId, "A", YESTERDAY, "100.00");
        observe(cardId, "B", YESTERDAY, "120.00");
        observe(cardId, "C", YESTERDAY, "110.00");
        // Today: A=105 (still there, repriced), D=90 (new). B and C delisted.
        observe(cardId, "A", TODAY, "105.00");
        observe(cardId, "D", TODAY, "90.00");

        aggregator.aggregate(TODAY);

        MarketMetricDaily row = metric(cardId, TODAY, "RAW", "RAW");
        assertThat(row.getFloorPrice()).isEqualByComparingTo("90.00");
        assertThat(row.getMedianAsk()).isEqualByComparingTo("97.5");
        assertThat(row.getActiveListings()).isEqualTo(2);
        assertThat(row.getNewListings()).isEqualTo(1);
        assertThat(row.getDelistings()).isEqualTo(2);
    }

    @Test
    void groupsSeparatelyByGrade() {
        UUID cardId = saveTestCard();
        observe(cardId, "raw-1", TODAY, "50.00", "RAW", "RAW");
        observe(cardId, "psa10-1", TODAY, "300.00", "PSA", "10");

        aggregator.aggregate(TODAY);

        assertThat(metric(cardId, TODAY, "RAW", "RAW").getFloorPrice()).isEqualByComparingTo("50.00");
        assertThat(metric(cardId, TODAY, "PSA", "10").getFloorPrice()).isEqualByComparingTo("300.00");
    }

    @Test
    void cardWithNoObservationsToday_getsNoRowForToday() {
        UUID cardId = saveTestCard();
        observe(cardId, "A", YESTERDAY, "100.00");
        // nothing observed today

        aggregator.aggregate(TODAY);

        assertThat(marketMetricDailyRepository.countByCardIdAndMetricDate(cardId, TODAY)).isZero();
    }

    @Test
    void reRunningAggregateForSameDayUpdatesInPlace() {
        UUID cardId = saveTestCard();
        observe(cardId, "A", TODAY, "100.00");
        aggregator.aggregate(TODAY);

        observe(cardId, "E", TODAY, "20.00");
        aggregator.aggregate(TODAY);

        assertThat(marketMetricDailyRepository.countByCardIdAndMetricDate(cardId, TODAY)).isEqualTo(1);
        MarketMetricDaily row = metric(cardId, TODAY, "RAW", "RAW");
        assertThat(row.getFloorPrice()).isEqualByComparingTo("20.00");
        assertThat(row.getActiveListings()).isEqualTo(2);
    }

    private MarketMetricDaily metric(UUID cardId, LocalDate date, String gradeSource, String gradeValue) {
        return marketMetricDailyRepository
                .findById(new MarketMetricDailyId(cardId, gradeSource, gradeValue, date))
                .orElseThrow();
    }

    private void observe(UUID cardId, String listingId, LocalDate date, String askPrice) {
        observe(cardId, listingId, date, askPrice, "RAW", "RAW");
    }

    private void observe(UUID cardId, String listingId, LocalDate date, String askPrice,
            String gradeSource, String gradeValue) {
        Instant observedAt = date.atTime(LocalTime.NOON).toInstant(ZoneOffset.UTC);
        saveObservation(cardId, listingId, observedAt, new BigDecimal(askPrice), gradeSource, gradeValue, null);
    }
}

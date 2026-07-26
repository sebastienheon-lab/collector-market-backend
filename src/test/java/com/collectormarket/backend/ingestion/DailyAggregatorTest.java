package com.collectormarket.backend.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class DailyAggregatorTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 6, 15);
    private static final LocalDate YESTERDAY = TODAY.minusDays(1);

    @Autowired
    private DailyAggregator aggregator;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void computesFloorMedianActiveNewAndDelistings() {
        UUID cardId = insertTestCard();

        // Yesterday: A=100, B=120, C=110
        insertObservation(cardId, "A", YESTERDAY, "100.00");
        insertObservation(cardId, "B", YESTERDAY, "120.00");
        insertObservation(cardId, "C", YESTERDAY, "110.00");
        // Today: A=105 (still there, repriced), D=90 (new). B and C delisted.
        insertObservation(cardId, "A", TODAY, "105.00");
        insertObservation(cardId, "D", TODAY, "90.00");

        aggregator.aggregate(TODAY);

        Map<String, Object> row = metricRow(cardId, TODAY);
        assertThat(row.get("floor_price")).isEqualTo(new BigDecimal("90.00"));
        assertThat(((BigDecimal) row.get("median_ask")))
                .isEqualByComparingTo(new BigDecimal("97.5"));
        assertThat(row.get("active_listings")).isEqualTo(2);
        assertThat(row.get("new_listings")).isEqualTo(1);
        assertThat(row.get("delistings")).isEqualTo(2);
    }

    @Test
    void groupsSeparatelyByGrade() {
        UUID cardId = insertTestCard();
        insertObservation(cardId, "raw-1", TODAY, "50.00", "RAW", "RAW");
        insertObservation(cardId, "psa10-1", TODAY, "300.00", "PSA", "10");

        aggregator.aggregate(TODAY);

        Map<String, Object> rawRow = metricRow(cardId, TODAY, "RAW", "RAW");
        Map<String, Object> psaRow = metricRow(cardId, TODAY, "PSA", "10");
        assertThat(rawRow.get("floor_price")).isEqualTo(new BigDecimal("50.00"));
        assertThat(psaRow.get("floor_price")).isEqualTo(new BigDecimal("300.00"));
    }

    @Test
    void cardWithNoObservationsToday_getsNoRowForToday() {
        UUID cardId = insertTestCard();
        insertObservation(cardId, "A", YESTERDAY, "100.00");
        // nothing observed today

        aggregator.aggregate(TODAY);

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT * FROM market_metric_daily WHERE card_id = ? AND metric_date = ?", cardId, TODAY);
        assertThat(rows).isEmpty();
    }

    @Test
    void reRunningAggregateForSameDayUpdatesInPlace() {
        UUID cardId = insertTestCard();
        insertObservation(cardId, "A", TODAY, "100.00");
        aggregator.aggregate(TODAY);

        insertObservation(cardId, "E", TODAY, "20.00");
        aggregator.aggregate(TODAY);

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT * FROM market_metric_daily WHERE card_id = ? AND metric_date = ?", cardId, TODAY);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("floor_price")).isEqualTo(new BigDecimal("20.00"));
        assertThat(rows.get(0).get("active_listings")).isEqualTo(2);
    }

    private Map<String, Object> metricRow(UUID cardId, LocalDate date) {
        return metricRow(cardId, date, "RAW", "RAW");
    }

    private Map<String, Object> metricRow(UUID cardId, LocalDate date, String gradeSource, String gradeValue) {
        return jdbcTemplate.queryForMap(
                "SELECT * FROM market_metric_daily WHERE card_id = ? AND metric_date = ? "
                        + "AND grade_source = ? AND grade_value = ?",
                cardId, date, gradeSource, gradeValue);
    }

    private void insertObservation(UUID cardId, String listingId, LocalDate date, String askPrice) {
        insertObservation(cardId, listingId, date, askPrice, "RAW", "RAW");
    }

    private void insertObservation(
            UUID cardId, String listingId, LocalDate date, String askPrice, String gradeSource, String gradeValue) {
        Timestamp observedAt = Timestamp.from(date.atTime(LocalTime.NOON).toInstant(ZoneOffset.UTC));
        jdbcTemplate.update("""
                INSERT INTO listing_observation
                    (card_id, external_listing_id, observed_at, ask_price, listing_format, grade_source, grade_value)
                VALUES (?, ?, ?, ?, 'FIXED_PRICE', ?, ?)
                """, cardId, listingId, observedAt, new BigDecimal(askPrice), gradeSource, gradeValue);
    }

    private UUID insertTestCard() {
        return jdbcTemplate.queryForObject("""
                INSERT INTO card (player_name, year, brand, set_name, sport_id, is_rookie)
                VALUES (?, 2023, 'Test Brand', 'Test Set', (SELECT id FROM sport WHERE code = 'baseball'), true)
                RETURNING id
                """, UUID.class, "Test Player " + UUID.randomUUID());
    }
}

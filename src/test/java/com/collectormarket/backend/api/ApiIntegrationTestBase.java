package com.collectormarket.backend.api;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;

import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

/**
 * Shared Testcontainers + MockMvc harness for the API tests. Uses the singleton-container pattern:
 * one Postgres started once for the whole JVM run and never stopped (Ryuk reaps it), so the cached
 * Spring context stays valid across every subclass. A per-class {@code @Container} would be stopped
 * after the first class finishes while the cached context still pointed at it - hence the manual
 * static start here instead. Flyway - including V013's pg_trgm extension + trigram index - runs
 * against this throwaway Postgres (superuser), so search is exercised for real.
 */
@SpringBootTest
@AutoConfigureMockMvc
abstract class ApiIntegrationTestBase {

    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    static {
        postgres.start();
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    /** Inserts a card and returns its id. Unique synthetic player names avoid clashing with the seed. */
    protected UUID insertCard(String player, int year, String brand, String setName, String cardNumber) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO card (player_name, year, brand, set_name, card_number, sport_id, is_rookie)
                VALUES (?, ?, ?, ?, ?, (SELECT id FROM sport WHERE code = 'baseball'), true)
                RETURNING id
                """, UUID.class, player, year, brand, setName, cardNumber);
    }

    protected void insertSale(UUID cardId, String priceType, String gradeSource, String gradeValue,
                              String price, Instant soldAt) {
        jdbcTemplate.update("""
                INSERT INTO price_snapshot
                    (card_id, sale_price, sold_at, price_type, source, grade_source, grade_value, platform, external_url)
                VALUES (?, ?, ?, ?, 'TEST', ?, ?, 'EBAY', 'https://ebay.com/itm/x')
                """, cardId, new BigDecimal(price), Timestamp.from(soldAt), priceType, gradeSource, gradeValue);
    }

    protected void insertMetricDaily(UUID cardId, String gradeSource, String gradeValue,
                                     LocalDate date, String floor, String median, int active) {
        jdbcTemplate.update("""
                INSERT INTO market_metric_daily
                    (card_id, grade_source, grade_value, metric_date, floor_price, median_ask, active_listings)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, cardId, gradeSource, gradeValue, date, new BigDecimal(floor), new BigDecimal(median), active);
    }

    protected void insertObservation(UUID cardId, String listingId, String gradeSource, String gradeValue,
                                     String ask, Instant observedAt) {
        jdbcTemplate.update("""
                INSERT INTO listing_observation
                    (card_id, external_listing_id, observed_at, ask_price, listing_format,
                     grade_source, grade_value, external_url)
                VALUES (?, ?, ?, ?, 'FIXED_PRICE', ?, ?, 'https://ebay.com/itm/' || ?)
                """, cardId, listingId, Timestamp.from(observedAt), new BigDecimal(ask),
                gradeSource, gradeValue, listingId);
    }

    protected static Instant daysAgo(int days) {
        return Instant.now().minus(days, ChronoUnit.DAYS);
    }
}

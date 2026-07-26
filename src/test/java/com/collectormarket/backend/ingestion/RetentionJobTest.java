package com.collectormarket.backend.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
class RetentionJobTest {

    @Autowired
    private RetentionJob retentionJob;

    @Autowired
    private RetentionProperties retentionProperties;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void deletesListingObservationsOlderThanTheWindow_keepsNewerOnes() {
        UUID cardId = insertTestCard();
        int windowDays = retentionProperties.listingObservationDays();

        UUID oldId = insertObservation(cardId, "old-listing", windowDays + 1);
        UUID freshId = insertObservation(cardId, "fresh-listing", windowDays - 1);

        retentionJob.run();

        assertThat(observationExists(oldId)).isFalse();
        assertThat(observationExists(freshId)).isTrue();
    }

    @Test
    void nullifiesLinkbackFieldsOlderThanTheWindow_keepsTransactionData() {
        UUID cardId = insertTestCard();
        int windowDays = retentionProperties.priceSnapshotLinkbackDays();

        UUID oldSnapshotId = insertPriceSnapshot(cardId, "old-tx", windowDays + 1);

        retentionJob.run();

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT * FROM price_snapshot WHERE id = ?", oldSnapshotId);
        assertThat(row.get("external_url")).isNull();
        assertThat(row.get("raw_title")).isNull();
        assertThat(row.get("external_id")).isNull();
        // permanent transaction data untouched
        assertThat(row.get("sale_price")).isEqualTo(new BigDecimal("42.00"));
        assertThat(row.get("card_id")).isEqualTo(cardId);
    }

    @Test
    void keepsLinkbackFieldsWithinTheWindow() {
        UUID cardId = insertTestCard();
        int windowDays = retentionProperties.priceSnapshotLinkbackDays();

        UUID freshSnapshotId = insertPriceSnapshot(cardId, "fresh-tx", windowDays - 1);

        retentionJob.run();

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT * FROM price_snapshot WHERE id = ?", freshSnapshotId);
        assertThat(row.get("external_url")).isNotNull();
        assertThat(row.get("raw_title")).isNotNull();
        assertThat(row.get("external_id")).isNotNull();
    }

    private boolean observationExists(UUID id) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM listing_observation WHERE id = ?", Integer.class, id);
        return count != null && count > 0;
    }

    private UUID insertObservation(UUID cardId, String listingId, int ageDays) {
        Timestamp observedAt = Timestamp.from(Instant.now().minus(ageDays, ChronoUnit.DAYS));
        return jdbcTemplate.queryForObject("""
                INSERT INTO listing_observation
                    (card_id, external_listing_id, observed_at, ask_price, listing_format, grade_source, grade_value)
                VALUES (?, ?, ?, 25.00, 'FIXED_PRICE', 'RAW', 'RAW')
                RETURNING id
                """, UUID.class, cardId, listingId, observedAt);
    }

    private UUID insertPriceSnapshot(UUID cardId, String externalId, int ageDays) {
        Timestamp soldAt = Timestamp.from(Instant.now().minus(ageDays, ChronoUnit.DAYS));
        return jdbcTemplate.queryForObject("""
                INSERT INTO price_snapshot
                    (card_id, sale_price, sold_at, price_type, source, platform, external_id, external_url, raw_title)
                VALUES (?, 42.00, ?, 'INFERRED_SALE', 'EBAY_BROWSE_INFERRED', 'EBAY', ?, ?, ?)
                RETURNING id
                """, UUID.class, cardId, soldAt, externalId,
                "https://ebay.com/itm/" + externalId, "Test Listing " + externalId);
    }

    private UUID insertTestCard() {
        return jdbcTemplate.queryForObject("""
                INSERT INTO card (player_name, year, brand, set_name, sport_id, is_rookie)
                VALUES (?, 2023, 'Test Brand', 'Test Set', (SELECT id FROM sport WHERE code = 'baseball'), true)
                RETURNING id
                """, UUID.class, "Test Player " + UUID.randomUUID());
    }
}

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
class InferredSaleDetectorTest {

    @Autowired
    private InferredSaleDetector detector;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void quantityIncreaseWritesOneInferredSalePerUnit() {
        UUID cardId = insertTestCard();
        String listingId = "listing-" + UUID.randomUUID();
        insertPriorObservation(cardId, listingId, 2);

        detector.detect(observation(cardId, listingId, "FIXED_PRICE", 5));

        List<Map<String, Object>> rows = priceSnapshotRows(listingId);
        assertThat(rows).hasSize(3);
        assertThat(rows).allSatisfy(row -> {
            assertThat(row.get("price_type")).isEqualTo("INFERRED_SALE");
            assertThat(row.get("source")).isEqualTo("EBAY_BROWSE_INFERRED");
            assertThat(((BigDecimal) row.get("sale_price"))).isEqualByComparingTo("25.00");
        });
        // distinct synthetic external_ids, one per inferred unit
        assertThat(rows.stream().map(r -> r.get("external_id")).distinct()).hasSize(3);
    }

    @Test
    void noPriorObservation_writesNothing() {
        UUID cardId = insertTestCard();
        String listingId = "listing-" + UUID.randomUUID();

        detector.detect(observation(cardId, listingId, "FIXED_PRICE", 1));

        assertThat(priceSnapshotRows(listingId)).isEmpty();
    }

    @Test
    void unchangedQuantity_writesNothing() {
        UUID cardId = insertTestCard();
        String listingId = "listing-" + UUID.randomUUID();
        insertPriorObservation(cardId, listingId, 3);

        detector.detect(observation(cardId, listingId, "FIXED_PRICE", 3));

        assertThat(priceSnapshotRows(listingId)).isEmpty();
    }

    @Test
    void decreasedQuantity_isIgnored() {
        UUID cardId = insertTestCard();
        String listingId = "listing-" + UUID.randomUUID();
        insertPriorObservation(cardId, listingId, 5);

        detector.detect(observation(cardId, listingId, "FIXED_PRICE", 2));

        assertThat(priceSnapshotRows(listingId)).isEmpty();
    }

    @Test
    void auctionListings_areNeverInferred() {
        UUID cardId = insertTestCard();
        String listingId = "listing-" + UUID.randomUUID();
        insertPriorObservation(cardId, listingId, 0);

        detector.detect(observation(cardId, listingId, "AUCTION", 5));

        assertThat(priceSnapshotRows(listingId)).isEmpty();
    }

    @Test
    void nullQuantitySold_writesNothing() {
        UUID cardId = insertTestCard();
        String listingId = "listing-" + UUID.randomUUID();
        insertPriorObservation(cardId, listingId, 1);

        detector.detect(new ObservationInput(
                cardId, listingId, Instant.now(), new BigDecimal("25.00"), "FIXED_PRICE", null,
                "RAW", "RAW", "https://ebay.com/itm/" + listingId, "Test Listing"));

        assertThat(priceSnapshotRows(listingId)).isEmpty();
    }

    private ObservationInput observation(UUID cardId, String listingId, String format, int quantitySold) {
        return new ObservationInput(
                cardId, listingId, Instant.now(), new BigDecimal("25.00"), format, quantitySold,
                "RAW", "RAW", "https://ebay.com/itm/" + listingId, "Test Listing");
    }

    private void insertPriorObservation(UUID cardId, String listingId, int quantitySold) {
        jdbcTemplate.update("""
                INSERT INTO listing_observation
                    (card_id, external_listing_id, observed_at, ask_price, listing_format, quantity_sold)
                VALUES (?, ?, ?, ?, 'FIXED_PRICE', ?)
                """,
                cardId, listingId, Timestamp.from(Instant.now().minus(1, ChronoUnit.DAYS)),
                new BigDecimal("25.00"), quantitySold);
    }

    private List<Map<String, Object>> priceSnapshotRows(String listingIdPrefix) {
        return jdbcTemplate.queryForList(
                "SELECT * FROM price_snapshot WHERE external_id LIKE ?", listingIdPrefix + "%");
    }

    private UUID insertTestCard() {
        return jdbcTemplate.queryForObject("""
                INSERT INTO card (player_name, year, brand, set_name, sport_id, is_rookie)
                VALUES (?, 2023, 'Test Brand', 'Test Set', (SELECT id FROM sport WHERE code = 'baseball'), true)
                RETURNING id
                """, UUID.class, "Test Player " + UUID.randomUUID());
    }
}

package com.collectormarket.backend.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.collectormarket.backend.ebay.EbayBrowseClient;
import com.collectormarket.backend.ebay.dto.ItemDetail;
import com.collectormarket.backend.ebay.dto.ItemSearchResult;
import com.collectormarket.backend.ebay.dto.ItemSummary;
import com.collectormarket.backend.ebay.dto.ListingFormat;

import reactor.core.publisher.Mono;

/**
 * M5 DoD, end to end against a real (throwaway) Postgres: poll -&gt; observation -&gt; aggregate
 * -&gt; retention. The eBay client is mocked (no live calls); everything downstream of it -
 * CardTracker, Poller, InferredSaleDetector, DailyAggregator, RetentionJob - runs for real.
 * <p>
 * The full app context boots here, which means CardSeedLoader/CardTracker seed-track all ~159
 * catalog cards. Those are deleted right after startup so this test controls exactly one card
 * and one listing, keeping the eBay mock simple and avoiding 159x network-shaped mock calls.
 */
@Testcontainers
@SpringBootTest
class IngestionPipelineIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @MockitoBean
    private EbayBrowseClient ebayBrowseClient;

    @Autowired
    private Poller poller;

    @Autowired
    private DailyAggregator dailyAggregator;

    @Autowired
    private RetentionJob retentionJob;

    @Autowired
    private RetentionProperties retentionProperties;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final String ITEM_ID = "e2e-item-1";

    @Test
    void pollObservationAggregateRetention() {
        jdbcTemplate.update("DELETE FROM card_tracking"); // isolate from the ~159 auto-seeded cards
        UUID cardId = insertTrackedTestCard();

        when(ebayBrowseClient.searchItems(anyString(), anyString()))
                .thenReturn(Mono.just(searchResultWithOneFixedPriceItem()));
        when(ebayBrowseClient.getItem(ITEM_ID))
                .thenReturn(Mono.just(itemDetail(2)))
                .thenReturn(Mono.just(itemDetail(5)));

        // --- Day 1 poll: first-ever observation, no prior to compare against -> no inferred sale.
        poller.pollDueCards();

        List<Map<String, Object>> observationsAfterDay1 = jdbcTemplate.queryForList(
                "SELECT * FROM listing_observation WHERE external_listing_id = ?", ITEM_ID);
        assertThat(observationsAfterDay1).hasSize(1);
        assertThat(observationsAfterDay1.get(0).get("quantity_sold")).isEqualTo(2);
        assertThat(priceSnapshotCount()).isZero();

        // simulate "the next day is due" without waiting a real 24h cadence
        jdbcTemplate.update("UPDATE card_tracking SET last_polled_at = NULL WHERE card_id = ?", cardId);

        // --- Day 2 poll: quantity_sold 2 -> 5, a delta of 3 -> 3 inferred-sale rows.
        poller.pollDueCards();

        List<Map<String, Object>> observationsAfterDay2 = jdbcTemplate.queryForList(
                "SELECT * FROM listing_observation WHERE external_listing_id = ? ORDER BY observed_at", ITEM_ID);
        assertThat(observationsAfterDay2).hasSize(2);
        assertThat(priceSnapshotCount()).isEqualTo(3);

        List<Map<String, Object>> inferredSales = jdbcTemplate.queryForList(
                "SELECT * FROM price_snapshot WHERE card_id = ? AND price_type = 'INFERRED_SALE'", cardId);
        assertThat(inferredSales).allSatisfy(row ->
                assertThat(((BigDecimal) row.get("sale_price"))).isEqualByComparingTo("25.00"));

        // --- Aggregate today: one market_metric_daily row, floor/active reflect the one listing.
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        dailyAggregator.aggregate(today);

        Map<String, Object> metricRow = jdbcTemplate.queryForMap(
                "SELECT * FROM market_metric_daily WHERE card_id = ? AND metric_date = ?", cardId, today);
        assertThat(metricRow.get("floor_price")).isEqualTo(new BigDecimal("25.00"));
        assertThat(metricRow.get("active_listings")).isEqualTo(1);

        // --- Retention: backdate both observations and the inferred-sale linkback fields past
        // their windows, then confirm the job actually prunes them. Shifted relatively (not set
        // to one absolute value) so the two observation rows don't collide on their
        // (external_listing_id, observed_at) unique constraint.
        jdbcTemplate.update(
                "UPDATE listing_observation SET observed_at = observed_at - make_interval(days => ?) "
                        + "WHERE external_listing_id = ?",
                retentionProperties.listingObservationDays() + 1, ITEM_ID);
        jdbcTemplate.update(
                "UPDATE price_snapshot SET sold_at = sold_at - make_interval(days => ?) WHERE card_id = ?",
                retentionProperties.priceSnapshotLinkbackDays() + 1, cardId);

        retentionJob.run();

        Integer remainingObservations = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM listing_observation WHERE external_listing_id = ?", Integer.class, ITEM_ID);
        assertThat(remainingObservations).isZero();

        List<Map<String, Object>> snapshotsAfterRetention = jdbcTemplate.queryForList(
                "SELECT * FROM price_snapshot WHERE card_id = ?", cardId);
        assertThat(snapshotsAfterRetention).hasSize(3);
        assertThat(snapshotsAfterRetention).allSatisfy(row -> {
            assertThat(row.get("external_id")).isNull();
            assertThat(row.get("raw_title")).isNull();
            assertThat(row.get("external_url")).isNull();
            // permanent tuple survives retention
            assertThat(row.get("sale_price")).isNotNull();
        });
    }

    private int priceSnapshotCount() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM price_snapshot WHERE external_id LIKE ?", Integer.class, ITEM_ID + "%");
        return count != null ? count : 0;
    }

    private ItemSearchResult searchResultWithOneFixedPriceItem() {
        ItemSummary item = new ItemSummary(
                ITEM_ID, "2023 Test Brand E2E Test Player #1", new BigDecimal("25.00"), "USD",
                "Graded", "https://ebay.com/itm/" + ITEM_ID, "seller1", ListingFormat.FIXED_PRICE);
        return new ItemSearchResult(List.of(item), 1, 50, 0);
    }

    private ItemDetail itemDetail(int estimatedSoldQuantity) {
        return new ItemDetail(
                ITEM_ID, "2023 Test Brand E2E Test Player #1", new BigDecimal("25.00"), "USD",
                "Graded", "https://ebay.com/itm/" + ITEM_ID, "seller1", ListingFormat.FIXED_PRICE,
                10, estimatedSoldQuantity);
    }

    private UUID insertTrackedTestCard() {
        UUID cardId = jdbcTemplate.queryForObject("""
                INSERT INTO card (player_name, year, brand, set_name, sport_id, is_rookie)
                VALUES ('E2E Test Player', 2023, 'Test Brand', 'Test Set',
                        (SELECT id FROM sport WHERE code = 'baseball'), true)
                RETURNING id
                """, UUID.class);
        jdbcTemplate.update("""
                INSERT INTO card_tracking (card_id, tier, poll_cadence, last_engagement_at)
                VALUES (?, 'SEED', 'DAILY', now())
                """, cardId);
        return cardId;
    }
}

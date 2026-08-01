package com.collectormarket.backend.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.collectormarket.backend.entities.Card;
import com.collectormarket.backend.repositories.CardRepository;
import com.collectormarket.backend.entities.CardTracking;
import com.collectormarket.backend.repositories.CardTrackingRepository;
import com.collectormarket.backend.entities.ListingObservation;
import com.collectormarket.backend.repositories.ListingObservationRepository;
import com.collectormarket.backend.entities.MarketMetricDaily;
import com.collectormarket.backend.entities.MarketMetricDailyId;
import com.collectormarket.backend.repositories.MarketMetricDailyRepository;
import com.collectormarket.backend.entities.PriceSnapshot;
import com.collectormarket.backend.repositories.PriceSnapshotRepository;
import com.collectormarket.backend.entities.Sport;
import com.collectormarket.backend.repositories.SportRepository;
import com.collectormarket.backend.ebay.EbayBrowseClient;
import com.collectormarket.backend.dto.ItemDetail;
import com.collectormarket.backend.dto.ItemSearchResult;
import com.collectormarket.backend.dto.ItemSummary;
import com.collectormarket.backend.dto.ListingFormat;

import reactor.core.publisher.Mono;

/**
 * M5 DoD, end to end against a real (throwaway) Postgres: poll -&gt; observation -&gt; aggregate
 * -&gt; retention. The eBay client is mocked (no live calls); everything downstream of it -
 * CardTracker, Poller, InferredSaleDetector, DailyAggregator, RetentionJob - runs for real.
 * <p>
 * The full app context boots here, which means CardSeedLoader/CardTracker seed-track all ~159
 * catalog cards. Those are deleted right after startup so this test controls exactly one card
 * and one listing, keeping the eBay mock simple and avoiding 159x network-shaped mock calls.
 * <p>
 * Not {@code @Transactional}: the pipeline commits through its own repositories, so fixtures/reads
 * use plain {@code save}/finders (committed data, no flush games).
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
    private CardRepository cardRepository;

    @Autowired
    private SportRepository sportRepository;

    @Autowired
    private CardTrackingRepository cardTrackingRepository;

    @Autowired
    private ListingObservationRepository listingObservationRepository;

    @Autowired
    private PriceSnapshotRepository priceSnapshotRepository;

    @Autowired
    private MarketMetricDailyRepository marketMetricDailyRepository;

    private static final String ITEM_ID = "e2e-item-1";

    @Test
    void pollObservationAggregateRetention() {
        cardTrackingRepository.deleteAllInBatch(); // isolate from the ~159 auto-seeded cards
        UUID cardId = insertTrackedTestCard();

        when(ebayBrowseClient.searchItems(anyString(), anyString()))
                .thenReturn(Mono.just(searchResultWithOneFixedPriceItem()));
        when(ebayBrowseClient.getItem(ITEM_ID))
                .thenReturn(Mono.just(itemDetail(2)))
                .thenReturn(Mono.just(itemDetail(5)));

        // --- Day 1 poll: first-ever observation, no prior to compare against -> no inferred sale.
        poller.pollDueCards();

        List<ListingObservation> observationsAfterDay1 = listingObservationRepository.findByExternalListingId(ITEM_ID);
        assertThat(observationsAfterDay1).hasSize(1);
        assertThat(observationsAfterDay1.get(0).getQuantitySold()).isEqualTo(2);
        assertThat(inferredSaleCount()).isZero();

        // simulate "the next day is due" without waiting a real 24h cadence
        CardTracking tracking = cardTrackingRepository.findById(cardId).orElseThrow();
        tracking.setLastPolledAt(null);
        cardTrackingRepository.save(tracking);

        // --- Day 2 poll: quantity_sold 2 -> 5, a delta of 3 -> 3 inferred-sale rows.
        poller.pollDueCards();

        assertThat(listingObservationRepository.findByExternalListingId(ITEM_ID)).hasSize(2);
        assertThat(inferredSaleCount()).isEqualTo(3);
        assertThat(priceSnapshotRepository.findByCardId(cardId)).allSatisfy(row ->
                assertThat(row.getSalePrice()).isEqualByComparingTo("25.00"));

        // --- Aggregate today: one market_metric_daily row, floor/active reflect the one listing.
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        dailyAggregator.aggregate(today);

        MarketMetricDaily metricRow = marketMetricDailyRepository
                .findById(new MarketMetricDailyId(cardId, "RAW", "RAW", today)).orElseThrow();
        assertThat(metricRow.getFloorPrice()).isEqualByComparingTo("25.00");
        assertThat(metricRow.getActiveListings()).isEqualTo(1);

        // --- Retention: backdate both observations and the inferred-sale linkback fields past their
        // windows, then confirm the job prunes them. Shifted relatively (same delta on every row) so
        // the two observation rows don't collide on their (external_listing_id, observed_at) unique key.
        backdateObservations(ITEM_ID, retentionProperties.listingObservationDays() + 1);
        backdateSnapshots(cardId, retentionProperties.priceSnapshotLinkbackDays() + 1);

        retentionJob.run();

        assertThat(listingObservationRepository.findByExternalListingId(ITEM_ID)).isEmpty();

        List<PriceSnapshot> snapshotsAfterRetention = priceSnapshotRepository.findByCardId(cardId);
        assertThat(snapshotsAfterRetention).hasSize(3);
        assertThat(snapshotsAfterRetention).allSatisfy(row -> {
            assertThat(row.getExternalId()).isNull();
            assertThat(row.getRawTitle()).isNull();
            assertThat(row.getExternalUrl()).isNull();
            // permanent tuple survives retention
            assertThat(row.getSalePrice()).isNotNull();
        });
    }

    private int inferredSaleCount() {
        return priceSnapshotRepository.findByExternalIdStartingWith(ITEM_ID).size();
    }

    private void backdateObservations(String listingId, int days) {
        List<ListingObservation> rows = listingObservationRepository.findByExternalListingId(listingId);
        rows.forEach(o -> o.setObservedAt(o.getObservedAt().minus(days, ChronoUnit.DAYS)));
        listingObservationRepository.saveAll(rows);
    }

    private void backdateSnapshots(UUID cardId, int days) {
        List<PriceSnapshot> rows = priceSnapshotRepository.findByCardId(cardId);
        rows.forEach(s -> s.setSoldAt(s.getSoldAt().minus(days, ChronoUnit.DAYS)));
        priceSnapshotRepository.saveAll(rows);
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
        Sport baseball = sportRepository.findByCode("baseball").orElseThrow();
        Card card = cardRepository.save(new Card(
                "E2E Test Player", (short) 2023, "Test Brand", "Test Set", null, baseball, true));

        CardTracking tracking = new CardTracking(card.getId(), "SEED", "DAILY", Instant.now());
        cardTrackingRepository.save(tracking);
        return card.getId();
    }
}

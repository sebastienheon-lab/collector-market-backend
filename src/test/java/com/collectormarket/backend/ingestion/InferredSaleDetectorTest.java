package com.collectormarket.backend.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.collectormarket.backend.domain.PriceSnapshot;

class InferredSaleDetectorTest extends IngestionJpaTestBase {

    @Autowired
    private InferredSaleDetector detector;

    @Test
    void quantityIncreaseWritesOneInferredSalePerUnit() {
        UUID cardId = saveTestCard();
        String listingId = "listing-" + UUID.randomUUID();
        savePriorObservation(cardId, listingId, 2);

        detector.detect(observation(cardId, listingId, "FIXED_PRICE", 5));

        List<PriceSnapshot> rows = priceSnapshotRows(listingId);
        assertThat(rows).hasSize(3);
        assertThat(rows).allSatisfy(row -> {
            assertThat(row.getPriceType()).isEqualTo("INFERRED_SALE");
            assertThat(row.getSource()).isEqualTo("EBAY_BROWSE_INFERRED");
            assertThat(row.getSalePrice()).isEqualByComparingTo("25.00");
        });
        // distinct synthetic external_ids, one per inferred unit
        assertThat(rows.stream().map(PriceSnapshot::getExternalId).distinct()).hasSize(3);
    }

    @Test
    void noPriorObservation_writesNothing() {
        UUID cardId = saveTestCard();
        String listingId = "listing-" + UUID.randomUUID();

        detector.detect(observation(cardId, listingId, "FIXED_PRICE", 1));

        assertThat(priceSnapshotRows(listingId)).isEmpty();
    }

    @Test
    void unchangedQuantity_writesNothing() {
        UUID cardId = saveTestCard();
        String listingId = "listing-" + UUID.randomUUID();
        savePriorObservation(cardId, listingId, 3);

        detector.detect(observation(cardId, listingId, "FIXED_PRICE", 3));

        assertThat(priceSnapshotRows(listingId)).isEmpty();
    }

    @Test
    void decreasedQuantity_isIgnored() {
        UUID cardId = saveTestCard();
        String listingId = "listing-" + UUID.randomUUID();
        savePriorObservation(cardId, listingId, 5);

        detector.detect(observation(cardId, listingId, "FIXED_PRICE", 2));

        assertThat(priceSnapshotRows(listingId)).isEmpty();
    }

    @Test
    void auctionListings_areNeverInferred() {
        UUID cardId = saveTestCard();
        String listingId = "listing-" + UUID.randomUUID();
        savePriorObservation(cardId, listingId, 0);

        detector.detect(observation(cardId, listingId, "AUCTION", 5));

        assertThat(priceSnapshotRows(listingId)).isEmpty();
    }

    @Test
    void nullQuantitySold_writesNothing() {
        UUID cardId = saveTestCard();
        String listingId = "listing-" + UUID.randomUUID();
        savePriorObservation(cardId, listingId, 1);

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

    private void savePriorObservation(UUID cardId, String listingId, int quantitySold) {
        saveObservation(cardId, listingId, Instant.now().minus(1, ChronoUnit.DAYS),
                new BigDecimal("25.00"), "RAW", "RAW", quantitySold);
    }

    // Read via the repository, not raw SQL: a derived-query read auto-flushes the pending JPA
    // inserts first, so they're visible inside this @Transactional test's session.
    private List<PriceSnapshot> priceSnapshotRows(String listingIdPrefix) {
        return priceSnapshotRepository.findByExternalIdStartingWith(listingIdPrefix);
    }
}

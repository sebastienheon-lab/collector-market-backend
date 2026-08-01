package com.collectormarket.backend.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.collectormarket.backend.entities.PriceSnapshot;

class RetentionJobTest extends IngestionJpaTestBase {

    @Autowired
    private RetentionJob retentionJob;

    @Autowired
    private RetentionProperties retentionProperties;

    @Test
    void deletesListingObservationsOlderThanTheWindow_keepsNewerOnes() {
        UUID cardId = saveTestCard();
        int windowDays = retentionProperties.listingObservationDays();

        UUID oldId = saveObservation(cardId, "old-listing", daysAgo(windowDays + 1),
                new BigDecimal("25.00"), "RAW", "RAW", null).getId();
        UUID freshId = saveObservation(cardId, "fresh-listing", daysAgo(windowDays - 1),
                new BigDecimal("25.00"), "RAW", "RAW", null).getId();

        retentionJob.run();

        assertThat(listingObservationRepository.existsById(oldId)).isFalse();
        assertThat(listingObservationRepository.existsById(freshId)).isTrue();
    }

    @Test
    void nullifiesLinkbackFieldsOlderThanTheWindow_keepsTransactionData() {
        UUID cardId = saveTestCard();
        int windowDays = retentionProperties.priceSnapshotLinkbackDays();

        UUID oldSnapshotId = savePriceSnapshot(cardId, "old-tx", windowDays + 1);

        retentionJob.run();

        PriceSnapshot row = priceSnapshotRepository.findById(oldSnapshotId).orElseThrow();
        assertThat(row.getExternalUrl()).isNull();
        assertThat(row.getRawTitle()).isNull();
        assertThat(row.getExternalId()).isNull();
        // permanent transaction data untouched
        assertThat(row.getSalePrice()).isEqualByComparingTo("42.00");
        assertThat(row.getCardId()).isEqualTo(cardId);
    }

    @Test
    void keepsLinkbackFieldsWithinTheWindow() {
        UUID cardId = saveTestCard();
        int windowDays = retentionProperties.priceSnapshotLinkbackDays();

        UUID freshSnapshotId = savePriceSnapshot(cardId, "fresh-tx", windowDays - 1);

        retentionJob.run();

        PriceSnapshot row = priceSnapshotRepository.findById(freshSnapshotId).orElseThrow();
        assertThat(row.getExternalUrl()).isNotNull();
        assertThat(row.getRawTitle()).isNotNull();
        assertThat(row.getExternalId()).isNotNull();
    }

    private UUID savePriceSnapshot(UUID cardId, String externalId, int ageDays) {
        PriceSnapshot snapshot = new PriceSnapshot();
        snapshot.setCardId(cardId);
        snapshot.setSalePrice(new BigDecimal("42.00"));
        snapshot.setSoldAt(daysAgo(ageDays));
        snapshot.setPriceType("INFERRED_SALE");
        snapshot.setSource("EBAY_BROWSE_INFERRED");
        snapshot.setPlatform("EBAY");
        snapshot.setExternalId(externalId);
        snapshot.setExternalUrl("https://ebay.com/itm/" + externalId);
        snapshot.setRawTitle("Test Listing " + externalId);
        return priceSnapshotRepository.saveAndFlush(snapshot).getId();
    }

    private static Instant daysAgo(int days) {
        return Instant.now().minus(days, ChronoUnit.DAYS);
    }
}

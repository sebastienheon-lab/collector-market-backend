package com.collectormarket.backend.ingestion;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.collectormarket.backend.entities.Card;
import com.collectormarket.backend.repositories.CardRepository;
import com.collectormarket.backend.entities.CardTracking;
import com.collectormarket.backend.repositories.CardTrackingRepository;
import com.collectormarket.backend.entities.ListingObservation;
import com.collectormarket.backend.repositories.ListingObservationRepository;
import com.collectormarket.backend.repositories.PriceSnapshotRepository;
import com.collectormarket.backend.entities.Sport;
import com.collectormarket.backend.repositories.SportRepository;

/**
 * Shared JPA harness for the {@code @Transactional} ingestion tests. Fixtures are written with
 * {@code saveAndFlush}: within a single test transaction, the code under test runs {@code @Modifying}
 * / native queries that would NOT see rows still buffered in the Hibernate session, so each fixture
 * is flushed to the DB before the code under test executes. Subclasses read back through repositories
 * (which auto-flush) rather than raw SQL.
 */
@SpringBootTest
@Transactional
abstract class IngestionJpaTestBase {

    @Autowired
    protected CardRepository cardRepository;

    @Autowired
    protected SportRepository sportRepository;

    @Autowired
    protected CardTrackingRepository cardTrackingRepository;

    @Autowired
    protected ListingObservationRepository listingObservationRepository;

    @Autowired
    protected PriceSnapshotRepository priceSnapshotRepository;

    /** Inserts a baseball test card with a unique player name and returns its id. */
    protected UUID saveTestCard() {
        Sport baseball = sportRepository.findByCode("baseball").orElseThrow();
        Card card = new Card("Test Player " + UUID.randomUUID(), (short) 2023,
                "Test Brand", "Test Set", null, baseball, true);
        return cardRepository.saveAndFlush(card).getId();
    }

    /** Inserts a card_tracking row and returns its card id. */
    protected UUID saveTracking(UUID cardId, CardTier tier, PollCadence cadence,
            Instant lastPolledAt, Instant lastEngagementAt) {
        CardTracking tracking = new CardTracking();
        tracking.setCardId(cardId);
        tracking.setTier(tier.name());
        tracking.setPollCadence(cadence.name());
        tracking.setLastPolledAt(lastPolledAt);
        tracking.setLastEngagementAt(lastEngagementAt != null ? lastEngagementAt : Instant.now());
        cardTrackingRepository.saveAndFlush(tracking);
        return cardId;
    }

    /** Inserts a fixed-price listing_observation. */
    protected ListingObservation saveObservation(UUID cardId, String listingId, Instant observedAt,
            BigDecimal askPrice, String gradeSource, String gradeValue, Integer quantitySold) {
        ListingObservation observation = new ListingObservation();
        observation.setCardId(cardId);
        observation.setExternalListingId(listingId);
        observation.setObservedAt(observedAt);
        observation.setAskPrice(askPrice);
        observation.setListingFormat("FIXED_PRICE");
        observation.setGradeSource(gradeSource);
        observation.setGradeValue(gradeValue);
        observation.setQuantitySold(quantitySold);
        return listingObservationRepository.saveAndFlush(observation);
    }
}

package com.collectormarket.backend.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;

import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

import com.collectormarket.backend.domain.Card;
import com.collectormarket.backend.repositories.CardRepository;
import com.collectormarket.backend.domain.ListingObservation;
import com.collectormarket.backend.repositories.ListingObservationRepository;
import com.collectormarket.backend.domain.MarketMetricDaily;
import com.collectormarket.backend.repositories.MarketMetricDailyRepository;
import com.collectormarket.backend.domain.PriceSnapshot;
import com.collectormarket.backend.repositories.PriceSnapshotRepository;
import com.collectormarket.backend.domain.Sport;
import com.collectormarket.backend.repositories.SportRepository;

/**
 * Shared Testcontainers + MockMvc harness for the API tests. Uses the singleton-container pattern:
 * one Postgres started once for the whole JVM run and never stopped (Ryuk reaps it), so the cached
 * Spring context stays valid across every subclass. A per-class {@code @Container} would be stopped
 * after the first class finishes while the cached context still pointed at it - hence the manual
 * static start here instead. Flyway - including V013's pg_trgm extension + trigram index - runs
 * against this throwaway Postgres (superuser), so search is exercised for real.
 * <p>
 * Not {@code @Transactional}: fixtures are committed via repositories so the API endpoints (which
 * run in their own transactions) see them.
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
    protected CardRepository cardRepository;

    @Autowired
    protected SportRepository sportRepository;

    @Autowired
    protected PriceSnapshotRepository priceSnapshotRepository;

    @Autowired
    protected MarketMetricDailyRepository marketMetricDailyRepository;

    @Autowired
    protected ListingObservationRepository listingObservationRepository;

    /** Inserts a card and returns its id. Unique synthetic player names avoid clashing with the seed. */
    protected UUID insertCard(String player, int year, String brand, String setName, String cardNumber) {
        Sport baseball = sportRepository.findByCode("baseball").orElseThrow();
        Card card = new Card(player, (short) year, brand, setName, cardNumber, baseball, true);
        return cardRepository.save(card).getId();
    }

    protected void insertSale(UUID cardId, String priceType, String gradeSource, String gradeValue,
                              String price, Instant soldAt) {
        PriceSnapshot snapshot = new PriceSnapshot();
        snapshot.setCardId(cardId);
        snapshot.setSalePrice(new BigDecimal(price));
        snapshot.setSoldAt(soldAt);
        snapshot.setPriceType(priceType);
        snapshot.setSource("TEST");
        snapshot.setGradeSource(gradeSource);
        snapshot.setGradeValue(gradeValue);
        snapshot.setPlatform("EBAY");
        snapshot.setExternalUrl("https://ebay.com/itm/x");
        priceSnapshotRepository.save(snapshot);
    }

    protected void insertMetricDaily(UUID cardId, String gradeSource, String gradeValue,
                                     LocalDate date, String floor, String median, int active) {
        MarketMetricDaily metric = new MarketMetricDaily();
        metric.setCardId(cardId);
        metric.setGradeSource(gradeSource);
        metric.setGradeValue(gradeValue);
        metric.setMetricDate(date);
        metric.setFloorPrice(new BigDecimal(floor));
        metric.setMedianAsk(new BigDecimal(median));
        metric.setActiveListings(active);
        marketMetricDailyRepository.save(metric);
    }

    protected void insertObservation(UUID cardId, String listingId, String gradeSource, String gradeValue,
                                     String ask, Instant observedAt) {
        ListingObservation observation = new ListingObservation();
        observation.setCardId(cardId);
        observation.setExternalListingId(listingId);
        observation.setObservedAt(observedAt);
        observation.setAskPrice(new BigDecimal(ask));
        observation.setListingFormat("FIXED_PRICE");
        observation.setGradeSource(gradeSource);
        observation.setGradeValue(gradeValue);
        observation.setExternalUrl("https://ebay.com/itm/" + listingId);
        listingObservationRepository.save(observation);
    }

    protected static Instant daysAgo(int days) {
        return Instant.now().minus(days, ChronoUnit.DAYS);
    }
}

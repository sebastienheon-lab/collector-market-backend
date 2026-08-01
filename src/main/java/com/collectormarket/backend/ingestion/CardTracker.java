package com.collectormarket.backend.ingestion;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.collectormarket.backend.domain.CardTracking;
import com.collectormarket.backend.repositories.CardTrackingRepository;

/**
 * Reads/writes {@code card_tracking} per its §7.10 lifecycle, via {@link CardTrackingRepository}.
 * <p>
 * Per this session's M5 decision, user search does NOT insert tracking rows - only the seed
 * layer (this class, at startup) and the future Phase 2 watchlist feature do. The price-history
 * endpoint (M6) is expected to call {@link #recordEngagement} on click-through; full
 * demand-driven tracking (inserting on search) waits for Phase 2 accounts.
 */
@Component
@Order(2)
public class CardTracker implements ApplicationRunner {

    private static final int WEEKLY_CADENCE_DAYS = 7;

    private final CardTrackingRepository cardTrackingRepository;

    public CardTracker(CardTrackingRepository cardTrackingRepository) {
        this.cardTrackingRepository = cardTrackingRepository;
    }

    /**
     * Runs after {@code CardSeedLoader} (see its {@code @Order}) - every card without a
     * card_tracking row yet is assumed to be part of the curated seed and gets tier=SEED,
     * poll_cadence=DAILY. Cards that later enter {@code card} via unmatched-listing triage would
     * also pick up SEED tracking under this simplification; there's no other signal yet to tell
     * "curated seed" from "manually triaged" apart, and demand-driven tracking is Phase 2 anyway.
     */
    @Override
    public void run(ApplicationArguments args) {
        cardTrackingRepository.seedUntrackedCards();
    }

    public List<TrackedCard> selectDueForPoll(int dailyCadenceHours) {
        Instant now = Instant.now();
        Instant dailyCutoff = now.minus(dailyCadenceHours, ChronoUnit.HOURS);
        Instant weeklyCutoff = now.minus(WEEKLY_CADENCE_DAYS, ChronoUnit.DAYS);
        return cardTrackingRepository.selectDueForPoll(dailyCutoff, weeklyCutoff).stream()
                .map(CardTracker::toTrackedCard)
                .toList();
    }

    public void recordPolled(UUID cardId) {
        cardTrackingRepository.recordPolled(cardId);
    }

    /** No-op if the card isn't tracked - search/browsing alone never creates a tracking row. */
    public void recordEngagement(UUID cardId) {
        cardTrackingRepository.recordEngagement(cardId);
    }

    /**
     * §7.10: upserts to tier=WATCHLISTED (or keeps SEED if already seeded), poll_cadence=DAILY.
     * Not called from anywhere yet - Phase 2 stub per the M5 checklist, ready for when the
     * watchlist feature and its table exist.
     */
    public void upsertWatchlisted(UUID cardId) {
        if (cardTrackingRepository.promoteToWatchlisted(cardId) == 0) {
            cardTrackingRepository.save(new CardTracking(cardId, "WATCHLISTED", "DAILY", Instant.now()));
        }
    }

    public int countByTier(CardTier tier) {
        return (int) cardTrackingRepository.countByTier(tier.name());
    }

    private static TrackedCard toTrackedCard(CardTracking tracking) {
        return new TrackedCard(
                tracking.getCardId(),
                CardTier.valueOf(tracking.getTier()),
                PollCadence.valueOf(tracking.getPollCadence()),
                tracking.getLastPolledAt(),
                tracking.getLastEngagementAt());
    }
}

package com.collectormarket.backend.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.collectormarket.backend.domain.CardTracking;

class CardTrackerTest extends IngestionJpaTestBase {

    private static final int DAILY_HOURS = 24;

    @Autowired
    private CardTracker cardTracker;

    @Test
    void selectDueForPoll_includesNeverPolledCards() {
        UUID cardId = saveTracking(saveTestCard(), CardTier.SEED, PollCadence.DAILY, null, null);

        List<TrackedCard> due = cardTracker.selectDueForPoll(DAILY_HOURS);

        assertThat(due).extracting(TrackedCard::cardId).contains(cardId);
    }

    @Test
    void selectDueForPoll_excludesRecentlyPolledDailyCard() {
        UUID cardId = saveTracking(saveTestCard(), CardTier.SEED, PollCadence.DAILY,
                Instant.now().minus(1, ChronoUnit.HOURS), null);

        List<TrackedCard> due = cardTracker.selectDueForPoll(DAILY_HOURS);

        assertThat(due).extracting(TrackedCard::cardId).doesNotContain(cardId);
    }

    @Test
    void selectDueForPoll_includesDailyCardPolledMoreThanCadenceHoursAgo() {
        UUID cardId = saveTracking(saveTestCard(), CardTier.SEED, PollCadence.DAILY,
                Instant.now().minus(25, ChronoUnit.HOURS), null);

        List<TrackedCard> due = cardTracker.selectDueForPoll(DAILY_HOURS);

        assertThat(due).extracting(TrackedCard::cardId).contains(cardId);
    }

    @Test
    void selectDueForPoll_weeklyCardNotDueAfterTwoDays() {
        UUID cardId = saveTracking(saveTestCard(), CardTier.DECAYED, PollCadence.WEEKLY,
                Instant.now().minus(2, ChronoUnit.DAYS), null);

        List<TrackedCard> due = cardTracker.selectDueForPoll(DAILY_HOURS);

        assertThat(due).extracting(TrackedCard::cardId).doesNotContain(cardId);
    }

    @Test
    void selectDueForPoll_weeklyCardDueAfterEightDays() {
        UUID cardId = saveTracking(saveTestCard(), CardTier.DECAYED, PollCadence.WEEKLY,
                Instant.now().minus(8, ChronoUnit.DAYS), null);

        List<TrackedCard> due = cardTracker.selectDueForPoll(DAILY_HOURS);

        assertThat(due).extracting(TrackedCard::cardId).contains(cardId);
    }

    @Test
    void selectDueForPoll_neverIncludesPausedCards() {
        UUID cardId = saveTracking(saveTestCard(), CardTier.DECAYED, PollCadence.PAUSED, null, null);

        List<TrackedCard> due = cardTracker.selectDueForPoll(DAILY_HOURS);

        assertThat(due).extracting(TrackedCard::cardId).doesNotContain(cardId);
    }

    @Test
    void recordPolled_updatesLastPolledAt() {
        UUID cardId = saveTracking(saveTestCard(), CardTier.SEED, PollCadence.DAILY, null, null);

        cardTracker.recordPolled(cardId);

        Instant lastPolledAt = cardTrackingRepository.findById(cardId).orElseThrow().getLastPolledAt();
        assertThat(lastPolledAt).isNotNull().isAfter(Instant.now().minusSeconds(10));
    }

    @Test
    void recordEngagement_updatesExistingRow() {
        UUID cardId = saveTracking(saveTestCard(), CardTier.SEARCHED, PollCadence.DAILY, null,
                Instant.now().minus(30, ChronoUnit.DAYS));

        cardTracker.recordEngagement(cardId);

        Instant lastEngagementAt = cardTrackingRepository.findById(cardId).orElseThrow().getLastEngagementAt();
        assertThat(lastEngagementAt).isAfter(Instant.now().minusSeconds(10));
    }

    @Test
    void recordEngagement_isNoOpForUntrackedCard() {
        UUID cardId = saveTestCard();

        cardTracker.recordEngagement(cardId);

        assertThat(cardTrackingRepository.existsById(cardId)).isFalse();
    }

    @Test
    void upsertWatchlisted_insertsWhenNotTracked() {
        UUID cardId = saveTestCard();

        cardTracker.upsertWatchlisted(cardId);

        CardTracking tracking = cardTrackingRepository.findById(cardId).orElseThrow();
        assertThat(tracking.getTier()).isEqualTo("WATCHLISTED");
    }

    @Test
    void upsertWatchlisted_keepsSeedTier() {
        UUID cardId = saveTracking(saveTestCard(), CardTier.SEED, PollCadence.DAILY, null, null);

        cardTracker.upsertWatchlisted(cardId);

        assertThat(cardTrackingRepository.findById(cardId).orElseThrow().getTier()).isEqualTo("SEED");
    }

    @Test
    void upsertWatchlisted_promotesDecayedToWatchlistedAndUnpausesCadence() {
        UUID cardId = saveTracking(saveTestCard(), CardTier.DECAYED, PollCadence.PAUSED, null, null);

        cardTracker.upsertWatchlisted(cardId);

        CardTracking tracking = cardTrackingRepository.findById(cardId).orElseThrow();
        assertThat(tracking.getTier()).isEqualTo("WATCHLISTED");
        assertThat(tracking.getPollCadence()).isEqualTo("DAILY");
    }
}

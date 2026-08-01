package com.collectormarket.backend.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.collectormarket.backend.entities.CardTracking;

class AgingJobTest extends IngestionJpaTestBase {

    // Matches app_setting's seeded defaults (V009): decay_after_days=14, pause_after_days=60.
    private static final int DECAY_AFTER_DAYS = 14;
    private static final int PAUSE_AFTER_DAYS = 60;

    @Autowired
    private AgingJob agingJob;

    @Test
    void searchedCardPastDecayThreshold_becomesDecayedWithWeeklyCadence() {
        UUID cardId = insertTracked(CardTier.SEARCHED, PollCadence.DAILY, DECAY_AFTER_DAYS + 5);

        agingJob.run();

        CardTracking row = tracking(cardId);
        assertThat(row.getTier()).isEqualTo("DECAYED");
        assertThat(row.getPollCadence()).isEqualTo("WEEKLY");
    }

    @Test
    void searchedCardWithinDecayThreshold_staysSearched() {
        UUID cardId = insertTracked(CardTier.SEARCHED, PollCadence.DAILY, DECAY_AFTER_DAYS - 5);

        agingJob.run();

        CardTracking row = tracking(cardId);
        assertThat(row.getTier()).isEqualTo("SEARCHED");
        assertThat(row.getPollCadence()).isEqualTo("DAILY");
    }

    @Test
    void decayedCardPastPauseThreshold_pausesCadenceButStaysDecayed() {
        UUID cardId = insertTracked(CardTier.DECAYED, PollCadence.WEEKLY, PAUSE_AFTER_DAYS + 5);

        agingJob.run();

        CardTracking row = tracking(cardId);
        assertThat(row.getTier()).isEqualTo("DECAYED");
        assertThat(row.getPollCadence()).isEqualTo("PAUSED");
    }

    @Test
    void decayedCardWithinPauseThreshold_keepsWeeklyCadence() {
        UUID cardId = insertTracked(CardTier.DECAYED, PollCadence.WEEKLY, DECAY_AFTER_DAYS + 5);

        agingJob.run();

        CardTracking row = tracking(cardId);
        assertThat(row.getTier()).isEqualTo("DECAYED");
        assertThat(row.getPollCadence()).isEqualTo("WEEKLY");
    }

    @Test
    void searchedCardIdleLongerThanBothThresholds_cascadesToPausedInOneRun() {
        UUID cardId = insertTracked(CardTier.SEARCHED, PollCadence.DAILY, PAUSE_AFTER_DAYS + 5);

        agingJob.run();

        CardTracking row = tracking(cardId);
        assertThat(row.getTier()).isEqualTo("DECAYED");
        assertThat(row.getPollCadence()).isEqualTo("PAUSED");
    }

    @Test
    void seedTierIsNeverTouchedRegardlessOfAge() {
        UUID cardId = insertTracked(CardTier.SEED, PollCadence.DAILY, PAUSE_AFTER_DAYS + 100);

        agingJob.run();

        CardTracking row = tracking(cardId);
        assertThat(row.getTier()).isEqualTo("SEED");
        assertThat(row.getPollCadence()).isEqualTo("DAILY");
    }

    @Test
    void watchlistedTierIsNeverTouchedRegardlessOfAge() {
        UUID cardId = insertTracked(CardTier.WATCHLISTED, PollCadence.DAILY, PAUSE_AFTER_DAYS + 100);

        agingJob.run();

        CardTracking row = tracking(cardId);
        assertThat(row.getTier()).isEqualTo("WATCHLISTED");
        assertThat(row.getPollCadence()).isEqualTo("DAILY");
    }

    private CardTracking tracking(UUID cardId) {
        return cardTrackingRepository.findById(cardId).orElseThrow();
    }

    private UUID insertTracked(CardTier tier, PollCadence cadence, int lastEngagementDaysAgo) {
        return saveTracking(saveTestCard(), tier, cadence, null,
                Instant.now().minus(lastEngagementDaysAgo, ChronoUnit.DAYS));
    }
}

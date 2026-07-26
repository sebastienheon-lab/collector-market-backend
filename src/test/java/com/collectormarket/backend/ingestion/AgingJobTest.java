package com.collectormarket.backend.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class AgingJobTest {

    // Matches app_setting's seeded defaults (V009): decay_after_days=14, pause_after_days=60.
    private static final int DECAY_AFTER_DAYS = 14;
    private static final int PAUSE_AFTER_DAYS = 60;

    @Autowired
    private AgingJob agingJob;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void searchedCardPastDecayThreshold_becomesDecayedWithWeeklyCadence() {
        UUID cardId = insertTracked(CardTier.SEARCHED, PollCadence.DAILY, DECAY_AFTER_DAYS + 5);

        agingJob.run();

        Map<String, Object> row = trackingRow(cardId);
        assertThat(row.get("tier")).isEqualTo("DECAYED");
        assertThat(row.get("poll_cadence")).isEqualTo("WEEKLY");
    }

    @Test
    void searchedCardWithinDecayThreshold_staysSearched() {
        UUID cardId = insertTracked(CardTier.SEARCHED, PollCadence.DAILY, DECAY_AFTER_DAYS - 5);

        agingJob.run();

        Map<String, Object> row = trackingRow(cardId);
        assertThat(row.get("tier")).isEqualTo("SEARCHED");
        assertThat(row.get("poll_cadence")).isEqualTo("DAILY");
    }

    @Test
    void decayedCardPastPauseThreshold_pausesCadenceButStaysDecayed() {
        UUID cardId = insertTracked(CardTier.DECAYED, PollCadence.WEEKLY, PAUSE_AFTER_DAYS + 5);

        agingJob.run();

        Map<String, Object> row = trackingRow(cardId);
        assertThat(row.get("tier")).isEqualTo("DECAYED");
        assertThat(row.get("poll_cadence")).isEqualTo("PAUSED");
    }

    @Test
    void decayedCardWithinPauseThreshold_keepsWeeklyCadence() {
        UUID cardId = insertTracked(CardTier.DECAYED, PollCadence.WEEKLY, DECAY_AFTER_DAYS + 5);

        agingJob.run();

        Map<String, Object> row = trackingRow(cardId);
        assertThat(row.get("tier")).isEqualTo("DECAYED");
        assertThat(row.get("poll_cadence")).isEqualTo("WEEKLY");
    }

    @Test
    void searchedCardIdleLongerThanBothThresholds_cascadesToPausedInOneRun() {
        UUID cardId = insertTracked(CardTier.SEARCHED, PollCadence.DAILY, PAUSE_AFTER_DAYS + 5);

        agingJob.run();

        Map<String, Object> row = trackingRow(cardId);
        assertThat(row.get("tier")).isEqualTo("DECAYED");
        assertThat(row.get("poll_cadence")).isEqualTo("PAUSED");
    }

    @Test
    void seedTierIsNeverTouchedRegardlessOfAge() {
        UUID cardId = insertTracked(CardTier.SEED, PollCadence.DAILY, PAUSE_AFTER_DAYS + 100);

        agingJob.run();

        Map<String, Object> row = trackingRow(cardId);
        assertThat(row.get("tier")).isEqualTo("SEED");
        assertThat(row.get("poll_cadence")).isEqualTo("DAILY");
    }

    @Test
    void watchlistedTierIsNeverTouchedRegardlessOfAge() {
        UUID cardId = insertTracked(CardTier.WATCHLISTED, PollCadence.DAILY, PAUSE_AFTER_DAYS + 100);

        agingJob.run();

        Map<String, Object> row = trackingRow(cardId);
        assertThat(row.get("tier")).isEqualTo("WATCHLISTED");
        assertThat(row.get("poll_cadence")).isEqualTo("DAILY");
    }

    private Map<String, Object> trackingRow(UUID cardId) {
        return jdbcTemplate.queryForMap("SELECT * FROM card_tracking WHERE card_id = ?", cardId);
    }

    private UUID insertTracked(CardTier tier, PollCadence cadence, int lastEngagementDaysAgo) {
        UUID cardId = insertTestCard();
        Timestamp lastEngagementAt = Timestamp.from(Instant.now().minus(lastEngagementDaysAgo, ChronoUnit.DAYS));
        jdbcTemplate.update("""
                INSERT INTO card_tracking (card_id, tier, poll_cadence, last_engagement_at)
                VALUES (?, ?, ?, ?)
                """, cardId, tier.name(), cadence.name(), lastEngagementAt);
        return cardId;
    }

    private UUID insertTestCard() {
        return jdbcTemplate.queryForObject("""
                INSERT INTO card (player_name, year, brand, set_name, sport_id, is_rookie)
                VALUES (?, 2023, 'Test Brand', 'Test Set', (SELECT id FROM sport WHERE code = 'baseball'), true)
                RETURNING id
                """, UUID.class, "Test Player " + UUID.randomUUID());
    }
}

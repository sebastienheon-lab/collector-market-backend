package com.collectormarket.backend.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class CardTrackerTest {

    private static final int DAILY_HOURS = 24;

    @Autowired
    private CardTracker cardTracker;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void selectDueForPoll_includesNeverPolledCards() {
        UUID cardId = insertTestCard();
        insertTracking(cardId, CardTier.SEED, PollCadence.DAILY, null);

        List<TrackedCard> due = cardTracker.selectDueForPoll(DAILY_HOURS);

        assertThat(due).extracting(TrackedCard::cardId).contains(cardId);
    }

    @Test
    void selectDueForPoll_excludesRecentlyPolledDailyCard() {
        UUID cardId = insertTestCard();
        insertTracking(cardId, CardTier.SEED, PollCadence.DAILY, Instant.now().minus(1, ChronoUnit.HOURS));

        List<TrackedCard> due = cardTracker.selectDueForPoll(DAILY_HOURS);

        assertThat(due).extracting(TrackedCard::cardId).doesNotContain(cardId);
    }

    @Test
    void selectDueForPoll_includesDailyCardPolledMoreThanCadenceHoursAgo() {
        UUID cardId = insertTestCard();
        insertTracking(cardId, CardTier.SEED, PollCadence.DAILY, Instant.now().minus(25, ChronoUnit.HOURS));

        List<TrackedCard> due = cardTracker.selectDueForPoll(DAILY_HOURS);

        assertThat(due).extracting(TrackedCard::cardId).contains(cardId);
    }

    @Test
    void selectDueForPoll_weeklyCardNotDueAfterTwoDays() {
        UUID cardId = insertTestCard();
        insertTracking(cardId, CardTier.DECAYED, PollCadence.WEEKLY, Instant.now().minus(2, ChronoUnit.DAYS));

        List<TrackedCard> due = cardTracker.selectDueForPoll(DAILY_HOURS);

        assertThat(due).extracting(TrackedCard::cardId).doesNotContain(cardId);
    }

    @Test
    void selectDueForPoll_weeklyCardDueAfterEightDays() {
        UUID cardId = insertTestCard();
        insertTracking(cardId, CardTier.DECAYED, PollCadence.WEEKLY, Instant.now().minus(8, ChronoUnit.DAYS));

        List<TrackedCard> due = cardTracker.selectDueForPoll(DAILY_HOURS);

        assertThat(due).extracting(TrackedCard::cardId).contains(cardId);
    }

    @Test
    void selectDueForPoll_neverIncludesPausedCards() {
        UUID cardId = insertTestCard();
        insertTracking(cardId, CardTier.DECAYED, PollCadence.PAUSED, null);

        List<TrackedCard> due = cardTracker.selectDueForPoll(DAILY_HOURS);

        assertThat(due).extracting(TrackedCard::cardId).doesNotContain(cardId);
    }

    @Test
    void recordPolled_updatesLastPolledAt() {
        UUID cardId = insertTestCard();
        insertTracking(cardId, CardTier.SEED, PollCadence.DAILY, null);

        cardTracker.recordPolled(cardId);

        Instant lastPolledAt = jdbcTemplate.queryForObject(
                "SELECT last_polled_at FROM card_tracking WHERE card_id = ?", Instant.class, cardId);
        assertThat(lastPolledAt).isNotNull().isAfter(Instant.now().minusSeconds(10));
    }

    @Test
    void recordEngagement_updatesExistingRow() {
        UUID cardId = insertTestCard();
        insertTracking(cardId, CardTier.SEARCHED, PollCadence.DAILY, null, Instant.now().minus(30, ChronoUnit.DAYS));

        cardTracker.recordEngagement(cardId);

        Instant lastEngagementAt = jdbcTemplate.queryForObject(
                "SELECT last_engagement_at FROM card_tracking WHERE card_id = ?", Instant.class, cardId);
        assertThat(lastEngagementAt).isAfter(Instant.now().minusSeconds(10));
    }

    @Test
    void recordEngagement_isNoOpForUntrackedCard() {
        UUID cardId = insertTestCard();

        cardTracker.recordEngagement(cardId);

        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM card_tracking WHERE card_id = ?", Integer.class, cardId);
        assertThat(count).isZero();
    }

    @Test
    void upsertWatchlisted_insertsWhenNotTracked() {
        UUID cardId = insertTestCard();

        cardTracker.upsertWatchlisted(cardId);

        String tier = jdbcTemplate.queryForObject(
                "SELECT tier FROM card_tracking WHERE card_id = ?", String.class, cardId);
        assertThat(tier).isEqualTo("WATCHLISTED");
    }

    @Test
    void upsertWatchlisted_keepsSeedTier() {
        UUID cardId = insertTestCard();
        insertTracking(cardId, CardTier.SEED, PollCadence.DAILY, null);

        cardTracker.upsertWatchlisted(cardId);

        String tier = jdbcTemplate.queryForObject(
                "SELECT tier FROM card_tracking WHERE card_id = ?", String.class, cardId);
        assertThat(tier).isEqualTo("SEED");
    }

    @Test
    void upsertWatchlisted_promotesDecayedToWatchlistedAndUnpausesCadence() {
        UUID cardId = insertTestCard();
        insertTracking(cardId, CardTier.DECAYED, PollCadence.PAUSED, null);

        cardTracker.upsertWatchlisted(cardId);

        String tier = jdbcTemplate.queryForObject(
                "SELECT tier FROM card_tracking WHERE card_id = ?", String.class, cardId);
        String cadence = jdbcTemplate.queryForObject(
                "SELECT poll_cadence FROM card_tracking WHERE card_id = ?", String.class, cardId);
        assertThat(tier).isEqualTo("WATCHLISTED");
        assertThat(cadence).isEqualTo("DAILY");
    }

    private UUID insertTestCard() {
        return jdbcTemplate.queryForObject("""
                INSERT INTO card (player_name, year, brand, set_name, sport_id, is_rookie)
                VALUES (?, 2023, 'Test Brand', 'Test Set', (SELECT id FROM sport WHERE code = 'baseball'), true)
                RETURNING id
                """, UUID.class, "Test Player " + UUID.randomUUID());
    }

    private void insertTracking(UUID cardId, CardTier tier, PollCadence cadence, Instant lastPolledAt) {
        insertTracking(cardId, tier, cadence, lastPolledAt, Instant.now());
    }

    private void insertTracking(
            UUID cardId, CardTier tier, PollCadence cadence, Instant lastPolledAt, Instant lastEngagementAt) {
        // pgjdbc's setObject() can't infer a SQL type for a bare java.time.Instant - needs
        // java.sql.Timestamp (production code never hits this since writes use now() SQL-side).
        jdbcTemplate.update("""
                INSERT INTO card_tracking (card_id, tier, poll_cadence, last_polled_at, last_engagement_at)
                VALUES (?, ?, ?, ?, ?)
                """, cardId, tier.name(), cadence.name(),
                toTimestamp(lastPolledAt), toTimestamp(lastEngagementAt));
    }

    private Timestamp toTimestamp(Instant instant) {
        return instant != null ? Timestamp.from(instant) : null;
    }
}

package com.collectormarket.backend.ingestion;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Reads/writes {@code card_tracking} per its §7.10 lifecycle.
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

    private final JdbcTemplate jdbcTemplate;
    private final AppSettingService appSettingService;

    public CardTracker(JdbcTemplate jdbcTemplate, AppSettingService appSettingService) {
        this.jdbcTemplate = jdbcTemplate;
        this.appSettingService = appSettingService;
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
        jdbcTemplate.update("""
                INSERT INTO card_tracking (card_id, tier, poll_cadence, last_engagement_at, added_at)
                SELECT id, 'SEED', 'DAILY', now(), now() FROM card c
                WHERE NOT EXISTS (SELECT 1 FROM card_tracking ct WHERE ct.card_id = c.id)
                """);
    }

    public List<TrackedCard> selectDueForPoll(int dailyCadenceHours) {
        return jdbcTemplate.query("""
                SELECT card_id, tier, poll_cadence, last_polled_at, last_engagement_at
                FROM card_tracking
                WHERE poll_cadence <> 'PAUSED'
                  AND (
                    (poll_cadence = 'DAILY'
                        AND (last_polled_at IS NULL OR last_polled_at < now() - make_interval(hours => ?)))
                    OR
                    (poll_cadence = 'WEEKLY'
                        AND (last_polled_at IS NULL OR last_polled_at < now() - make_interval(days => ?)))
                  )
                ORDER BY last_polled_at NULLS FIRST
                """,
                (rs, rowNum) -> new TrackedCard(
                        (UUID) rs.getObject("card_id"),
                        CardTier.valueOf(rs.getString("tier")),
                        PollCadence.valueOf(rs.getString("poll_cadence")),
                        toInstant(rs.getTimestamp("last_polled_at")),
                        toInstant(rs.getTimestamp("last_engagement_at"))),
                dailyCadenceHours, WEEKLY_CADENCE_DAYS);
    }

    public void recordPolled(UUID cardId) {
        jdbcTemplate.update("UPDATE card_tracking SET last_polled_at = now() WHERE card_id = ?", cardId);
    }

    /** Count of cards currently in the given tier - backs the {@code cards.tracked} gauge (M7). */
    public int countByTier(CardTier tier) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM card_tracking WHERE tier = ?", Integer.class, tier.name());
        return count != null ? count : 0;
    }

    /** No-op if the card isn't tracked - search/browsing alone never creates a tracking row. */
    public void recordEngagement(UUID cardId) {
        jdbcTemplate.update("UPDATE card_tracking SET last_engagement_at = now() WHERE card_id = ?", cardId);
    }

    /**
     * §7.10: upserts to tier=WATCHLISTED (or keeps SEED if already seeded), poll_cadence=DAILY.
     * Not called from anywhere yet - Phase 2 stub per the M5 checklist, ready for when the
     * watchlist feature and its table exist.
     */
    public void upsertWatchlisted(UUID cardId) {
        int updated = jdbcTemplate.update("""
                UPDATE card_tracking
                SET tier = CASE WHEN tier = 'SEED' THEN tier ELSE 'WATCHLISTED' END,
                    poll_cadence = 'DAILY',
                    last_engagement_at = now()
                WHERE card_id = ?
                """, cardId);

        if (updated == 0) {
            jdbcTemplate.update("""
                    INSERT INTO card_tracking (card_id, tier, poll_cadence, last_engagement_at, added_at)
                    VALUES (?, 'WATCHLISTED', 'DAILY', now(), now())
                    """, cardId);
        }
    }

    private Instant toInstant(Timestamp timestamp) {
        return timestamp != null ? timestamp.toInstant() : null;
    }
}

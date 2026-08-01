package com.collectormarket.backend.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/**
 * Spring Data repository for {@link CardTracking} (§7.10). The set-based lifecycle operations (seed,
 * poll/engagement stamps, aging demotions) are {@code @Modifying} bulk statements so they run in one
 * round-trip; time thresholds are passed as pre-computed cutoffs (no {@code make_interval}).
 */
public interface CardTrackingRepository extends JpaRepository<CardTracking, UUID> {

    long countByTier(String tier);

    /** Seeds a SEED/DAILY row for every card that doesn't yet have a tracking row (startup). */
    @Transactional
    @Modifying
    @Query(value = """
            INSERT INTO card_tracking (card_id, tier, poll_cadence, last_engagement_at, added_at)
            SELECT id, 'SEED', 'DAILY', now(), now() FROM card c
            WHERE NOT EXISTS (SELECT 1 FROM card_tracking ct WHERE ct.card_id = c.id)
            """, nativeQuery = true)
    int seedUntrackedCards();

    /**
     * Cards due for a poll: not PAUSED, and past their cadence cutoff (DAILY vs WEEKLY), oldest
     * poll first. Callers pass the cutoffs so the runtime-tunable daily cadence stays honoured.
     */
    @Query("""
            SELECT ct FROM CardTracking ct
            WHERE ct.pollCadence <> 'PAUSED' AND (
                (ct.pollCadence = 'DAILY'  AND (ct.lastPolledAt IS NULL OR ct.lastPolledAt < :dailyCutoff))
             OR (ct.pollCadence = 'WEEKLY' AND (ct.lastPolledAt IS NULL OR ct.lastPolledAt < :weeklyCutoff))
            )
            ORDER BY ct.lastPolledAt ASC NULLS FIRST
            """)
    List<CardTracking> selectDueForPoll(
            @Param("dailyCutoff") Instant dailyCutoff, @Param("weeklyCutoff") Instant weeklyCutoff);

    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("UPDATE CardTracking ct SET ct.lastPolledAt = CURRENT_TIMESTAMP WHERE ct.cardId = :cardId")
    int recordPolled(@Param("cardId") UUID cardId);

    /** No-op (0 rows) if the card isn't tracked - search/browsing alone never creates a row. */
    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("UPDATE CardTracking ct SET ct.lastEngagementAt = CURRENT_TIMESTAMP WHERE ct.cardId = :cardId")
    int recordEngagement(@Param("cardId") UUID cardId);

    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("""
            UPDATE CardTracking ct SET ct.tier = 'DECAYED', ct.pollCadence = 'WEEKLY'
            WHERE ct.tier = 'SEARCHED' AND ct.lastEngagementAt < :cutoff
            """)
    int decaySearchedCards(@Param("cutoff") Instant cutoff);

    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("""
            UPDATE CardTracking ct SET ct.pollCadence = 'PAUSED'
            WHERE ct.tier = 'DECAYED' AND ct.pollCadence <> 'PAUSED' AND ct.lastEngagementAt < :cutoff
            """)
    int pauseDecayedCards(@Param("cutoff") Instant cutoff);

    /** §7.10 watchlist upsert (update half): keep SEED, otherwise promote to WATCHLISTED/DAILY. */
    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("""
            UPDATE CardTracking ct
            SET ct.tier = CASE WHEN ct.tier = 'SEED' THEN ct.tier ELSE 'WATCHLISTED' END,
                ct.pollCadence = 'DAILY', ct.lastEngagementAt = CURRENT_TIMESTAMP
            WHERE ct.cardId = :cardId
            """)
    int promoteToWatchlisted(@Param("cardId") UUID cardId);
}

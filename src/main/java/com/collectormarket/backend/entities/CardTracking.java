package com.collectormarket.backend.entities;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * JPA mapping of {@code card_tracking} (§7.10) - poller inclusion & priority tiers. The PK is the
 * {@code card_id} (assigned, not generated). Schema owned by Flyway (V007). {@code tier} and
 * {@code poll_cadence} are stored as their string codes (kept as {@link String} to match the native
 * upsert/aging SQL rather than {@code @Enumerated}). {@code added_at} is DB-defaulted and unmapped.
 */
@Entity
@Table(name = "card_tracking")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class CardTracking {

    @Id
    @Column(name = "card_id")
    private UUID cardId;

    @Column(nullable = false)
    private String tier;

    @Column(name = "poll_cadence", nullable = false)
    private String pollCadence;

    @Column(name = "last_polled_at")
    private Instant lastPolledAt;

    @Column(name = "last_engagement_at", nullable = false)
    private Instant lastEngagementAt;

    /** Creates a new tracking row (used by the watchlist upsert's insert half). */
    public CardTracking(UUID cardId, String tier, String pollCadence, Instant lastEngagementAt) {
        this.cardId = cardId;
        this.tier = tier;
        this.pollCadence = pollCadence;
        this.lastEngagementAt = lastEngagementAt;
    }
}

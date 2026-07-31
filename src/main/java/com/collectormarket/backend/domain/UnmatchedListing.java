package com.collectormarket.backend.domain;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * JPA mapping of {@code unmatched_listing} (§10) - staging queue for eBay titles the matcher
 * couldn't resolve. Schema owned by Flyway (V011). {@code extracted_fields} is the JSONB column,
 * carried as a pre-serialized JSON string ({@code @JdbcTypeCode(JSON)} binds it as {@code jsonb});
 * the DB-defaulted {@code created_at} is left unmapped (validate ignores unmapped columns).
 */
@Entity
@Table(name = "unmatched_listing")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UnmatchedListing {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "raw_title", nullable = false)
    private String rawTitle;

    @Column(name = "observed_at", nullable = false)
    private Instant observedAt;

    @Column(name = "matched_card_id")
    private UUID matchedCardId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "extracted_fields")
    private String extractedFields;

    /** Records a new unmatched title for triage. The id is generated on insert. */
    public UnmatchedListing(String rawTitle, Instant observedAt, String extractedFields) {
        this.rawTitle = rawTitle;
        this.observedAt = observedAt;
        this.extractedFields = extractedFields;
    }
}

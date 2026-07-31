package com.collectormarket.backend.domain;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * JPA mapping of the {@code card} catalog table (§7.1). Part of the JPA pilot alongside {@link
 * Sport}. The schema is owned by Flyway ({@code V002}) and inserts still go through
 * {@code CardSeedLoader} (JdbcTemplate), so this entity is read-only in practice - no
 * {@code @GeneratedValue} on the DB-defaulted UUID id, and the audit columns
 * ({@code created_at}/{@code updated_at}) are intentionally left unmapped (validate ignores
 * unmapped columns).
 * <p>
 * The {@code sport} association is LAZY (JPA defaults to-one to EAGER); readers that need the sport
 * code fetch it explicitly via {@code CardRepository#findWithSportById}'s entity graph to avoid a
 * lazy-load after the session closes ({@code open-in-view} is false).
 * <p>
 * Lombok is limited to {@code @Getter}/{@code @Setter} + a protected no-arg constructor -
 * deliberately NOT {@code @Data}/{@code @ToString}/{@code @EqualsAndHashCode}, which are unsafe on
 * entities (they would span the lazy {@code sport} association and break entity-identity
 * semantics). {@code @Getter} yields {@code isRookie()} for the primitive-boolean field.
 */
@Entity
@Table(name = "card")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Card {

    @Id
    private UUID id;

    @Column(name = "player_name", nullable = false)
    private String playerName;

    @Column(name = "year", nullable = false)
    private Short year;

    @Column(name = "brand")
    private String brand;

    @Column(name = "set_name")
    private String setName;

    @Column(name = "card_number")
    private String cardNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sport_id", nullable = false)
    private Sport sport;

    @Column(name = "is_rookie")
    private boolean rookie;

    @Column(name = "parallel")
    private String parallel;

    @Column(name = "print_run")
    private Integer printRun;
}

package com.collectormarket.backend.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

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
 * JPA mapping of {@code listing_observation} (§7.4) - one row per tracked listing per observation
 * day (ask data). Schema owned by Flyway (V004). {@code card_id} is a raw {@link UUID}; the
 * DB-defaulted {@code created_at} is left unmapped.
 */
@Entity
@Table(name = "listing_observation")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class ListingObservation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "card_id", nullable = false)
    private UUID cardId;

    @Column(name = "external_listing_id", nullable = false)
    private String externalListingId;

    @Column(name = "observed_at", nullable = false)
    private Instant observedAt;

    @Column(name = "ask_price", nullable = false)
    private BigDecimal askPrice;

    @Column(name = "listing_format", nullable = false)
    private String listingFormat;

    @Column(name = "quantity_sold")
    private Integer quantitySold;

    @Column(name = "grade_source")
    private String gradeSource;

    @Column(name = "grade_value")
    private String gradeValue;

    @Column(name = "external_url")
    private String externalUrl;

    @Column(name = "raw_title")
    private String rawTitle;
}

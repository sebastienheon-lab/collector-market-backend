package com.collectormarket.backend.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/** Spring Data repository for {@link ListingObservation}. */
public interface ListingObservationRepository extends JpaRepository<ListingObservation, UUID> {

    /** Latest observation of a given eBay listing - the prior row for inferred-sale detection. */
    Optional<ListingObservation> findFirstByExternalListingIdOrderByObservedAtDesc(String externalListingId);

    List<ListingObservation> findByExternalListingId(String externalListingId);

    /**
     * Idempotent insert: the {@code ON CONFLICT (external_listing_id, observed_at) DO NOTHING}
     * dedup has no JPQL equivalent, so it stays a native statement (executed via Hibernate).
     */
    @Transactional
    @Modifying
    @Query(value = """
            INSERT INTO listing_observation
                (card_id, external_listing_id, observed_at, ask_price, listing_format, quantity_sold,
                 grade_source, grade_value, external_url, raw_title)
            VALUES (:cardId, :externalListingId, :observedAt, :askPrice, :listingFormat, :quantitySold,
                    :gradeSource, :gradeValue, :externalUrl, :rawTitle)
            ON CONFLICT (external_listing_id, observed_at) DO NOTHING
            """, nativeQuery = true)
    void insertIfAbsent(
            @Param("cardId") UUID cardId,
            @Param("externalListingId") String externalListingId,
            @Param("observedAt") Instant observedAt,
            @Param("askPrice") BigDecimal askPrice,
            @Param("listingFormat") String listingFormat,
            @Param("quantitySold") Integer quantitySold,
            @Param("gradeSource") String gradeSource,
            @Param("gradeValue") String gradeValue,
            @Param("externalUrl") String externalUrl,
            @Param("rawTitle") String rawTitle);

    /** §5.4 retention: delete observations older than the cutoff. Returns the row count. */
    @Transactional
    @Modifying
    @Query("DELETE FROM ListingObservation lo WHERE lo.observedAt < :cutoff")
    int deleteObservedBefore(@Param("cutoff") Instant cutoff);
}

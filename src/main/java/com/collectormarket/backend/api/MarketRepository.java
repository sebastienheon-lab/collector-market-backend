package com.collectormarket.backend.api;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import com.collectormarket.backend.api.dto.ActiveListing;
import com.collectormarket.backend.domain.ListingObservation;

/**
 * Current market view (§8.3): live listings from the most recent day of {@code listing_observation}
 * for the card (+ optional grade). {@code DISTINCT ON} collapses a listing observed twice that day
 * to its latest row - no JPQL equivalent, so it's native. A null {@code gradeSource} means "all
 * grades" (the {@code ALL} token).
 */
public interface MarketRepository extends Repository<ListingObservation, UUID> {

    @Query(value = """
            SELECT DISTINCT ON (external_listing_id)
                   ask_price AS askPrice, listing_format AS listingFormat, external_url AS externalUrl
            FROM listing_observation
            WHERE card_id = :cardId
              AND (:gradeSource IS NULL OR (grade_source = :gradeSource AND grade_value = :gradeValue))
              AND observed_at::date = (
                  SELECT max(observed_at::date) FROM listing_observation
                  WHERE card_id = :cardId
                    AND (:gradeSource IS NULL OR (grade_source = :gradeSource AND grade_value = :gradeValue))
              )
            ORDER BY external_listing_id, observed_at DESC
            """, nativeQuery = true)
    List<ActiveListing> currentListings(
            @Param("cardId") UUID cardId,
            @Param("gradeSource") String gradeSource,
            @Param("gradeValue") String gradeValue);
}

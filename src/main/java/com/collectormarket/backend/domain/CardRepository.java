package com.collectormarket.backend.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data repository for {@link Card} - the front door for card CRUD/simple reads. {@code
 * existsById} (inherited) backs the shared existence guard. {@link #findWithSportById} eagerly
 * fetches the {@code sport} association in one query so the detail DTO can read the sport code
 * without a lazy load after the session closes. The catalog-matching finders back the normalizer
 * ({@code CardCatalogLookup}) and the seed loader.
 */
public interface CardRepository extends JpaRepository<Card, UUID> {

    @EntityGraph(attributePaths = "sport")
    Optional<Card> findWithSportById(UUID id);

    /** The maintained player-name list (§10), derived from the catalog itself. */
    @Query("SELECT DISTINCT c.playerName FROM Card c")
    List<String> findDistinctPlayerNames();

    /**
     * Exact catalog match (§10 Phase 1, no fuzzy). {@code setName}/{@code cardNumber} act as
     * confirming filters only: a null argument, or a catalog row whose {@code cardNumber} is still
     * null, doesn't block a match on player+year+brand(+set).
     */
    @Query("""
            SELECT c.id FROM Card c
            WHERE c.playerName = :playerName AND c.year = :year AND c.brand = :brand
              AND (:setName IS NULL OR c.setName = :setName)
              AND (:cardNumber IS NULL OR c.cardNumber IS NULL OR c.cardNumber = :cardNumber)
            """)
    List<UUID> findExactMatchIds(
            @Param("playerName") String playerName,
            @Param("year") Short year,
            @Param("brand") String brand,
            @Param("setName") String setName,
            @Param("cardNumber") String cardNumber);

    /**
     * Natural-key lookup for the idempotent seed upsert: (player, year, brand, set), null-safe on
     * {@code brand}/{@code setName} (matching the {@code IS NOT DISTINCT FROM} semantics). Returns a
     * list because the {@code card} table has no unique constraint on the natural key.
     */
    @Query("""
            SELECT c FROM Card c
            WHERE c.playerName = :playerName AND c.year = :year
              AND ((:brand IS NULL AND c.brand IS NULL) OR c.brand = :brand)
              AND ((:setName IS NULL AND c.setName IS NULL) OR c.setName = :setName)
            """)
    List<Card> findByNaturalKey(
            @Param("playerName") String playerName,
            @Param("year") Short year,
            @Param("brand") String brand,
            @Param("setName") String setName);
}

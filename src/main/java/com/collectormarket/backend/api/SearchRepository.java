package com.collectormarket.backend.api;

import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import com.collectormarket.backend.api.dto.SearchResultItem;
import com.collectormarket.backend.domain.Card;

/**
 * Postgres-native card search (§8.1). When the query carries name text it ranks by trigram
 * similarity ({@code %} / {@code similarity()}, backed by the V013 GIN index); otherwise it orders
 * by year. Both share the optional year/card-number/sport filters. Results project straight onto the
 * {@link SearchResultItem} DTO; {@code year} is cast to int for the record's primitive component.
 */
public interface SearchRepository extends Repository<Card, java.util.UUID> {

    String SELECT = """
            SELECT c.id AS id, c.player_name AS playerName, CAST(c.year AS int) AS year,
                   c.set_name AS setName, c.card_number AS cardNumber, s.code AS sport,
                   c.is_rookie AS isRookie,
                   ls.sale_price AS latestSalePrice, ls.sold_at::date AS latestSaleDate
            FROM card c
            JOIN sport s ON s.id = c.sport_id
            LEFT JOIN LATERAL (
                SELECT sale_price, sold_at FROM price_snapshot ps
                WHERE ps.card_id = c.id ORDER BY sold_at DESC LIMIT 1
            ) ls ON true
            """;

    String FILTERS = """
            AND (:year IS NULL OR c.year = :year)
            AND (:cardNumber IS NULL OR c.card_number = :cardNumber)
            AND (:sportId IS NULL OR c.sport_id = :sportId)
            """;

    @Query(value = SELECT + "WHERE c.player_name % :playerTerm\n" + FILTERS
            + "ORDER BY similarity(c.player_name, :playerTerm) DESC, c.year DESC\n"
            + "LIMIT :limit OFFSET :offset", nativeQuery = true)
    List<SearchResultItem> rankedSearch(
            @Param("playerTerm") String playerTerm, @Param("year") Integer year,
            @Param("cardNumber") String cardNumber, @Param("sportId") Short sportId,
            @Param("limit") int limit, @Param("offset") int offset);

    @Query(value = SELECT + "WHERE 1 = 1\n" + FILTERS
            + "ORDER BY c.year DESC, c.player_name ASC\n"
            + "LIMIT :limit OFFSET :offset", nativeQuery = true)
    List<SearchResultItem> unrankedSearch(
            @Param("year") Integer year, @Param("cardNumber") String cardNumber,
            @Param("sportId") Short sportId, @Param("limit") int limit, @Param("offset") int offset);

    @Query(value = "SELECT count(*) FROM card c WHERE c.player_name % :playerTerm\n" + FILTERS,
            nativeQuery = true)
    long countRanked(
            @Param("playerTerm") String playerTerm, @Param("year") Integer year,
            @Param("cardNumber") String cardNumber, @Param("sportId") Short sportId);

    @Query(value = "SELECT count(*) FROM card c WHERE 1 = 1\n" + FILTERS, nativeQuery = true)
    long countUnranked(
            @Param("year") Integer year, @Param("cardNumber") String cardNumber,
            @Param("sportId") Short sportId);
}

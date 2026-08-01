package com.collectormarket.backend.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import com.collectormarket.backend.api.dto.RelatedCard;
import com.collectormarket.backend.domain.Card;

/**
 * Related-card suggestions for the empty state (F-11, §8.2). Same-set siblings project straight onto
 * {@link RelatedCard}; the different-grade rows come back as {@link GradeSaleRow} because their
 * {@code grade} token is computed in Java ({@code Grade.tokenFor}). {@code LATERAL} + {@code IS NOT
 * DISTINCT FROM} keep both queries native.
 */
public interface RelatedCardsRepository extends Repository<Card, UUID> {

    /** Raw latest-sale-per-grade row for the same card (grade token applied by the caller). */
    record GradeSaleRow(String gradeSource, String gradeValue, BigDecimal salePrice, LocalDate soldDate) {
    }

    @Query(value = """
            SELECT g.grade_source AS gradeSource, g.grade_value AS gradeValue,
                   ls.sale_price AS salePrice, ls.sold_at::date AS soldDate
            FROM (
                SELECT DISTINCT grade_source, grade_value
                FROM price_snapshot
                WHERE card_id = :cardId
                  AND price_type IN ('SOLD','INFERRED_SALE')
                  AND NOT (grade_source = :excludeGradeSource AND grade_value = :excludeGradeValue)
            ) g
            JOIN LATERAL (
                SELECT sale_price, sold_at FROM price_snapshot ps
                WHERE ps.card_id = :cardId
                  AND ps.grade_source = g.grade_source AND ps.grade_value = g.grade_value
                  AND ps.price_type IN ('SOLD','INFERRED_SALE')
                ORDER BY sold_at DESC LIMIT 1
            ) ls ON true
            ORDER BY ls.sold_at DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<GradeSaleRow> differentGradesOfSameCard(
            @Param("cardId") UUID cardId,
            @Param("excludeGradeSource") String excludeGradeSource,
            @Param("excludeGradeValue") String excludeGradeValue,
            @Param("limit") int limit);

    @Query(value = """
            SELECT c2.id AS cardId, CAST('SAME_SET_SAME_PLAYER' AS text) AS relation,
                   CAST(NULL AS text) AS grade, c2.card_number AS cardNumber,
                   ls.sale_price AS latestSalePrice, ls.sold_at::date AS latestSaleDate
            FROM card c1
            JOIN card c2
                ON c2.player_name = c1.player_name
               AND c2.year = c1.year
               AND c2.brand IS NOT DISTINCT FROM c1.brand
               AND c2.set_name IS NOT DISTINCT FROM c1.set_name
               AND c2.id <> c1.id
            LEFT JOIN LATERAL (
                SELECT sale_price, sold_at FROM price_snapshot ps
                WHERE ps.card_id = c2.id
                  AND ps.price_type IN ('SOLD','INFERRED_SALE')
                ORDER BY sold_at DESC LIMIT 1
            ) ls ON true
            WHERE c1.id = :cardId
            ORDER BY (ls.sold_at IS NOT NULL) DESC, ls.sold_at DESC NULLS LAST, c2.card_number ASC
            LIMIT :limit
            """, nativeQuery = true)
    List<RelatedCard> sameSetSiblings(@Param("cardId") UUID cardId, @Param("limit") int limit);
}

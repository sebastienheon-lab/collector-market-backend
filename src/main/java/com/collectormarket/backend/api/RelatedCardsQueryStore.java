package com.collectormarket.backend.api;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import com.collectormarket.backend.api.dto.RelatedCard;

/**
 * Related-card suggestions for the empty-state (F-11, OQ-5, §8.2). Similarity rule: same player,
 * same year - adjacent grades of the same card first ({@code SAME_CARD_DIFFERENT_GRADE}), then
 * other cards in the same set ({@code SAME_SET_SAME_PLAYER}). Reuses existing catalog + snapshot
 * data; no scoring. Same-set siblings are surfaced even when they have no sales yet (LEFT JOIN),
 * so the empty-state stays populated "when catalog siblings exist"; data-bearing siblings sort
 * first.
 */
@Component
public class RelatedCardsQueryStore {

    private static final String SALE_TYPES = "('SOLD','INFERRED_SALE')";

    private final JdbcTemplate jdbcTemplate;

    public RelatedCardsQueryStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * @param cardId the card whose price history is empty
     * @param grade  the requested grade; when it filters, its own grade is excluded from the
     *               different-grade suggestions
     * @param limit  max total suggestions
     */
    public List<RelatedCard> relatedCards(UUID cardId, Grade grade, int limit) {
        List<RelatedCard> out = new ArrayList<>(differentGrades(cardId, grade, limit));
        if (out.size() < limit) {
            out.addAll(sameSetSiblings(cardId, limit - out.size()));
        }
        return out;
    }

    /** Other grades of the SAME card that have sales (only meaningful when a specific grade was asked). */
    private List<RelatedCard> differentGrades(UUID cardId, Grade grade, int limit) {
        if (!grade.filtersByGrade()) {
            return List.of();
        }
        RowMapper<RelatedCard> mapper = (rs, rowNum) -> new RelatedCard(
                cardId,
                "SAME_CARD_DIFFERENT_GRADE",
                Grade.tokenFor(rs.getString("grade_source"), rs.getString("grade_value")),
                null,
                rs.getBigDecimal("sale_price"),
                rs.getObject("sold_date", LocalDate.class));

        return jdbcTemplate.query("""
                SELECT g.grade_source, g.grade_value, ls.sale_price, ls.sold_at::date AS sold_date
                FROM (
                    SELECT DISTINCT grade_source, grade_value
                    FROM price_snapshot
                    WHERE card_id = ?
                      AND price_type IN """ + SALE_TYPES + """
                      AND NOT (grade_source = ? AND grade_value = ?)
                ) g
                JOIN LATERAL (
                    SELECT sale_price, sold_at FROM price_snapshot ps
                    WHERE ps.card_id = ?
                      AND ps.grade_source = g.grade_source AND ps.grade_value = g.grade_value
                      AND ps.price_type IN """ + SALE_TYPES + """
                    ORDER BY sold_at DESC LIMIT 1
                ) ls ON true
                ORDER BY ls.sold_at DESC
                LIMIT ?
                """,
                mapper, cardId, grade.gradeSource(), grade.gradeValue(), cardId, limit);
    }

    /** Other cards in the same set (same player/year/brand/set), data-bearing ones first. */
    private List<RelatedCard> sameSetSiblings(UUID cardId, int limit) {
        RowMapper<RelatedCard> mapper = (rs, rowNum) -> new RelatedCard(
                rs.getObject("id", UUID.class),
                "SAME_SET_SAME_PLAYER",
                null,
                rs.getString("card_number"),
                rs.getBigDecimal("sale_price"),
                rs.getObject("sold_date", LocalDate.class));

        return jdbcTemplate.query("""
                SELECT c2.id, c2.card_number, ls.sale_price, ls.sold_at::date AS sold_date
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
                      AND ps.price_type IN """ + SALE_TYPES + """
                    ORDER BY sold_at DESC LIMIT 1
                ) ls ON true
                WHERE c1.id = ?
                ORDER BY (ls.sold_at IS NOT NULL) DESC, ls.sold_at DESC NULLS LAST, c2.card_number ASC
                LIMIT ?
                """,
                mapper, cardId, limit);
    }
}

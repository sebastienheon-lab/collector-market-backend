package com.collectormarket.backend.api;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.collectormarket.backend.api.RelatedCardsRepository.GradeSaleRow;
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

    private final RelatedCardsRepository relatedCardsRepository;

    public RelatedCardsQueryStore(RelatedCardsRepository relatedCardsRepository) {
        this.relatedCardsRepository = relatedCardsRepository;
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
            out.addAll(relatedCardsRepository.sameSetSiblings(cardId, limit - out.size()));
        }
        return out;
    }

    /** Other grades of the SAME card that have sales (only meaningful when a specific grade was asked). */
    private List<RelatedCard> differentGrades(UUID cardId, Grade grade, int limit) {
        if (!grade.filtersByGrade()) {
            return List.of();
        }
        List<GradeSaleRow> rows = relatedCardsRepository.differentGradesOfSameCard(
                cardId, grade.gradeSource(), grade.gradeValue(), limit);
        return rows.stream()
                .map(row -> new RelatedCard(
                        cardId,
                        "SAME_CARD_DIFFERENT_GRADE",
                        Grade.tokenFor(row.gradeSource(), row.gradeValue()),
                        null,
                        row.salePrice(),
                        row.soldDate()))
                .toList();
    }
}

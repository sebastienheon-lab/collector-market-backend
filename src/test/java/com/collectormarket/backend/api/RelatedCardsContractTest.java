package com.collectormarket.backend.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

/**
 * F-11 contract: a card whose primary series is empty (no {@code sales}, no {@code floorHistory})
 * returns a populated {@code relatedCards} array when catalog siblings exist - and, conversely, a
 * card that DOES have data returns an empty {@code relatedCards}.
 */
class RelatedCardsContractTest extends ApiIntegrationTestBase {

    @Test
    void emptyPrimarySeries_withSameSetSibling_populatesRelatedCards() throws Exception {
        String player = "F11 Set Player " + UUID.randomUUID();
        UUID primary = insertCard(player, 2024, "Topps", "Chrome", "1");
        UUID sibling = insertCard(player, 2024, "Topps", "Chrome", "2"); // same player/year/brand/set
        insertSale(sibling, "SOLD", "RAW", "RAW", "92.00", daysAgo(3));   // sibling has data for context

        mockMvc.perform(get("/api/v1/cards/{id}/prices", primary).param("grade", "ALL").param("days", "90"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sales.items.length()").value(0))
                .andExpect(jsonPath("$.floorHistory.length()").value(0))
                .andExpect(jsonPath("$.relatedCards.length()").value(Matchers.greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.relatedCards[?(@.cardId == '" + sibling + "')].relation",
                        Matchers.hasItem("SAME_SET_SAME_PLAYER")));
    }

    @Test
    void emptyPrimaryGrade_withOtherGradeOfSameCard_populatesDifferentGradeRelation() throws Exception {
        UUID card = insertCard("F11 Grade Player " + UUID.randomUUID(), 2024, "Topps", "Chrome", "1");
        // Requested grade (PSA10) is empty, but the same card has PSA9 sales.
        insertSale(card, "SOLD", "PSA", "9", "168.00", daysAgo(5));

        mockMvc.perform(get("/api/v1/cards/{id}/prices", card).param("grade", "PSA10").param("days", "90"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sales.items.length()").value(0))
                .andExpect(jsonPath("$.floorHistory.length()").value(0))
                .andExpect(jsonPath("$.relatedCards[?(@.cardId == '" + card + "')].relation",
                        Matchers.hasItem("SAME_CARD_DIFFERENT_GRADE")))
                .andExpect(jsonPath("$.relatedCards[?(@.relation == 'SAME_CARD_DIFFERENT_GRADE')].grade",
                        Matchers.hasItem("PSA9")));
    }

    @Test
    void nonEmptyPrimarySeries_returnsEmptyRelatedCards() throws Exception {
        String player = "F11 HasData Player " + UUID.randomUUID();
        UUID card = insertCard(player, 2024, "Topps", "Chrome", "1");
        insertCard(player, 2024, "Topps", "Chrome", "2"); // a sibling exists...
        insertSale(card, "SOLD", "RAW", "RAW", "50.00", daysAgo(2)); // ...but this card has its own data

        mockMvc.perform(get("/api/v1/cards/{id}/prices", card).param("grade", "ALL").param("days", "90"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sales.items.length()").value(1))
                .andExpect(jsonPath("$.relatedCards.length()").value(0));
    }
}

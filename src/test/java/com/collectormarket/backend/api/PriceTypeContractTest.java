package com.collectormarket.backend.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.hamcrest.Matchers;

/**
 * F-07 contract: the {@code sales} (transaction) series and {@code floorHistory} (ASK/floor) series
 * are separate arrays, a series never mixes price types, and ASK data never appears in {@code sales}.
 * <p>
 * The adversarial part: an {@code ASK}-typed row is inserted directly into {@code price_snapshot}
 * (the table has no CHECK on {@code price_type}), so this proves the query's
 * {@code price_type IN ('SOLD','INFERRED_SALE')} filter is what keeps ASK out - not merely the
 * ingestion side never writing one.
 */
class PriceTypeContractTest extends ApiIntegrationTestBase {

    @Test
    void salesNeverContainAsk_andFloorHistoryIsASeparateArray() throws Exception {
        UUID cardId = insertCard("F07 Contract Player " + UUID.randomUUID(), 2024, "Topps", "Chrome", "1");

        // Transaction series: one SOLD, one INFERRED_SALE at PSA 10.
        insertSale(cardId, "SOLD", "PSA", "10", "250.00", daysAgo(3));
        insertSale(cardId, "INFERRED_SALE", "PSA", "10", "240.00", daysAgo(10));
        // Adversarial ASK row that must be filtered out of sales.
        insertSale(cardId, "ASK", "PSA", "10", "999.99", daysAgo(1));
        // Floor/ASK series lives only in market_metric_daily.
        insertMetricDaily(cardId, "PSA", "10", java.time.LocalDate.now(), "259.99", "289.00", 14);

        mockMvc.perform(get("/api/v1/cards/{id}/prices", cardId).param("grade", "PSA10").param("days", "90"))
                .andExpect(status().isOk())
                // Two transaction rows only; the ASK row is excluded.
                .andExpect(jsonPath("$.sales.items.length()").value(2))
                .andExpect(jsonPath("$.summary.salesCount").value(2))
                // Every sale is a transaction type; never ASK.
                .andExpect(jsonPath("$.sales.items[*].priceType",
                        Matchers.everyItem(Matchers.in(java.util.List.of("SOLD", "INFERRED_SALE")))))
                .andExpect(jsonPath("$.sales.items[*].priceType", Matchers.not(Matchers.hasItem("ASK"))))
                .andExpect(jsonPath("$.sales.items[?(@.salePrice == 999.99)]", Matchers.empty()))
                // The floor series is a distinct, populated array - never merged into sales.
                .andExpect(jsonPath("$.floorHistory").isArray())
                .andExpect(jsonPath("$.floorHistory.length()").value(1))
                .andExpect(jsonPath("$.floorHistory[0].floorPrice").value(259.99))
                // Floor points carry no priceType - they're structurally a different series.
                .andExpect(jsonPath("$.floorHistory[0].priceType").doesNotExist());
    }
}

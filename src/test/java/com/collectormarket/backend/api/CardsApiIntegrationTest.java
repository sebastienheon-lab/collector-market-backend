package com.collectormarket.backend.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

/** Endpoint happy paths, RFC 7807 error mappings, and the M5 engagement touch on the prices route. */
class CardsApiIntegrationTest extends ApiIntegrationTestBase {

    @Test
    void search_findsCardByPlayerName() throws Exception {
        String player = "Zyxwvu Searchtarget " + UUID.randomUUID();
        UUID id = insertCard(player, 2024, "Topps", "Chrome", "1");

        mockMvc.perform(get("/api/v1/cards/search").param("q", player).param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[?(@.id == '" + id + "')]").exists())
                .andExpect(jsonPath("$.results[0].sport").value("baseball"))
                .andExpect(jsonPath("$.page").value(0));
    }

    @Test
    void cardDetail_returnsFields() throws Exception {
        UUID id = insertCard("Detail Player " + UUID.randomUUID(), 2023, "Panini", "Prizm", "12");

        mockMvc.perform(get("/api/v1/cards/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.year").value(2023))
                .andExpect(jsonPath("$.brand").value("Panini"))
                .andExpect(jsonPath("$.sport").value("baseball"));
    }

    @Test
    void cardDetail_unknownId_returns404ProblemJson() throws Exception {
        mockMvc.perform(get("/api/v1/cards/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("CARD_NOT_FOUND"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void market_computesFloorMedianAndListings() throws Exception {
        UUID id = insertCard("Market Player " + UUID.randomUUID(), 2024, "Topps", "Chrome", "1");
        insertObservation(id, "L1-" + id, "RAW", "RAW", "50.00", Instant.now());
        insertObservation(id, "L2-" + id, "RAW", "RAW", "70.00", Instant.now());

        mockMvc.perform(get("/api/v1/cards/{id}/market", id).param("grade", "ALL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.floorPrice").value(50.00))
                .andExpect(jsonPath("$.medianAsk").value(60.00))
                .andExpect(jsonPath("$.activeListings").value(2))
                .andExpect(jsonPath("$.listings.length()").value(2));
    }

    @Test
    void grades_returnsAveragePerGrade() throws Exception {
        UUID id = insertCard("Grades Player " + UUID.randomUUID(), 2024, "Topps", "Chrome", "1");
        insertSale(id, "SOLD", "PSA", "10", "300.00", daysAgo(2));
        insertSale(id, "SOLD", "RAW", "RAW", "40.00", daysAgo(2));

        mockMvc.perform(get("/api/v1/cards/{id}/grades", id).param("days", "90"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grades.length()").value(2))
                .andExpect(jsonPath("$.grades[*].grade",
                        Matchers.hasItems("PSA10", "RAW")));
    }

    @Test
    void prices_invalidDays_returns400InvalidDays() throws Exception {
        UUID id = insertCard("Days Player " + UUID.randomUUID(), 2024, "Topps", "Chrome", "1");

        mockMvc.perform(get("/api/v1/cards/{id}/prices", id).param("days", "45"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_DAYS"));
    }

    @Test
    void prices_invalidGrade_returns400InvalidGrade() throws Exception {
        UUID id = insertCard("Grade Player " + UUID.randomUUID(), 2024, "Topps", "Chrome", "1");

        mockMvc.perform(get("/api/v1/cards/{id}/prices", id).param("grade", "BOGUS"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_GRADE"));
    }

    @Test
    void prices_touchesLastEngagementAt() throws Exception {
        UUID id = insertCard("Engagement Player " + UUID.randomUUID(), 2024, "Topps", "Chrome", "1");
        // A tracked card whose engagement is stale; the prices endpoint should bump it (M5 decision).
        Instant tenDaysAgo = daysAgo(10);
        jdbcTemplate.update("""
                INSERT INTO card_tracking (card_id, tier, poll_cadence, last_engagement_at)
                VALUES (?, 'SEED', 'DAILY', ?)
                """, id, Timestamp.from(tenDaysAgo));

        mockMvc.perform(get("/api/v1/cards/{id}/prices", id).param("grade", "ALL").param("days", "90"))
                .andExpect(status().isOk());

        Timestamp engagement = jdbcTemplate.queryForObject(
                "SELECT last_engagement_at FROM card_tracking WHERE card_id = ?", Timestamp.class, id);
        assertThat(engagement.toInstant()).isAfter(daysAgo(1));
    }
}

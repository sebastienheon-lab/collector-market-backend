package com.collectormarket.backend.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.collectormarket.backend.dto.ItemDetail;
import com.collectormarket.backend.dto.ListingFormat;
import com.collectormarket.backend.ebay.EbayBrowseClient;
import com.collectormarket.backend.entities.CardTracking;
import com.collectormarket.backend.repositories.CardTrackingRepository;
import com.jayway.jsonpath.JsonPath;

import reactor.core.publisher.Mono;

/** Endpoint happy paths, RFC 7807 error mappings, and the M5 engagement touch on the prices route. */
class CardsApiIntegrationTest extends ApiIntegrationTestBase {

    @Autowired
    private CardTrackingRepository cardTrackingRepository;

    @MockitoBean
    private EbayBrowseClient ebayBrowseClient;

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
        String listing1 = "L1-" + id;
        String listing2 = "L2-" + id;
        insertObservation(id, listing1, "RAW", "RAW", "50.00", Instant.now());
        insertObservation(id, listing2, "RAW", "RAW", "70.00", Instant.now());

        // OQ-16: imageUrl is fetched live from the Browse API per listing, not stored - one listing
        // has an image, the other doesn't, to exercise the nullable case too.
        when(ebayBrowseClient.getItem(eq(listing1)))
                .thenReturn(Mono.just(itemDetail(listing1, "https://i.ebayimg.com/images/g/abc/s-l500.jpg")));
        when(ebayBrowseClient.getItem(eq(listing2)))
                .thenReturn(Mono.just(itemDetail(listing2, null)));

        mockMvc.perform(get("/api/v1/cards/{id}/market", id).param("grade", "ALL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.floorPrice").value(50.00))
                .andExpect(jsonPath("$.medianAsk").value(60.00))
                .andExpect(jsonPath("$.activeListings").value(2))
                .andExpect(jsonPath("$.listings.length()").value(2))
                .andExpect(jsonPath("$.listings[0].imageUrl")
                        .value("https://i.ebayimg.com/images/g/abc/s-l500.jpg"))
                .andExpect(jsonPath("$.listings[1].imageUrl").doesNotExist());
    }

    private ItemDetail itemDetail(String itemId, String imageUrl) {
        return new ItemDetail(itemId, "title", new BigDecimal("50.00"), "USD", "Graded",
                "https://ebay.com/itm/" + itemId, "seller1", ListingFormat.FIXED_PRICE, null, null, imageUrl);
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
        cardTrackingRepository.save(new CardTracking(id, "SEED", "DAILY", daysAgo(10)));

        mockMvc.perform(get("/api/v1/cards/{id}/prices", id).param("grade", "ALL").param("days", "90"))
                .andExpect(status().isOk());

        Instant engagement = cardTrackingRepository.findById(id).orElseThrow().getLastEngagementAt();
        assertThat(engagement).isAfter(daysAgo(1));
    }

    // --- Search: sport filter + pagination -----------------------------------------------------

    @Test
    void search_unknownSport_returns400InvalidSport() throws Exception {
        mockMvc.perform(get("/api/v1/cards/search").param("q", "anyone").param("sport", "quidditch"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_SPORT"));
    }

    @Test
    void search_paginatesWithTotalCount() throws Exception {
        String player = "Paginate Player " + UUID.randomUUID();
        insertCard(player, 2024, "Topps", "Chrome", "1");
        insertCard(player, 2024, "Topps", "Chrome", "2");
        insertCard(player, 2024, "Topps", "Chrome", "3");

        mockMvc.perform(get("/api/v1/cards/search").param("q", player).param("size", "2").param("page", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(3))
                .andExpect(jsonPath("$.results.length()").value(2))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2));

        mockMvc.perform(get("/api/v1/cards/search").param("q", player).param("size", "2").param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results.length()").value(1))
                .andExpect(jsonPath("$.page").value(1));
    }

    // --- Prices: cursor pagination + bad cursor ------------------------------------------------

    @Test
    void prices_cursorPaginatesSalesAcrossPages() throws Exception {
        UUID id = insertCard("Cursor Player " + UUID.randomUUID(), 2024, "Topps", "Chrome", "1");
        insertSale(id, "SOLD", "PSA", "10", "300.00", daysAgo(2));
        insertSale(id, "SOLD", "PSA", "10", "310.00", daysAgo(4));
        insertSale(id, "SOLD", "PSA", "10", "320.00", daysAgo(6));

        String firstPage = mockMvc.perform(get("/api/v1/cards/{id}/prices", id)
                        .param("grade", "PSA10").param("days", "90").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sales.items.length()").value(2))
                .andExpect(jsonPath("$.sales.nextCursor").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        String cursor = JsonPath.read(firstPage, "$.sales.nextCursor");

        mockMvc.perform(get("/api/v1/cards/{id}/prices", id)
                        .param("grade", "PSA10").param("days", "90").param("size", "2").param("cursor", cursor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sales.items.length()").value(1))   // the remaining sale
                .andExpect(jsonPath("$.sales.nextCursor").isEmpty());     // no further page
    }

    @Test
    void prices_invalidCursor_returns400InvalidCursor() throws Exception {
        UUID id = insertCard("BadCursor Player " + UUID.randomUUID(), 2024, "Topps", "Chrome", "1");

        mockMvc.perform(get("/api/v1/cards/{id}/prices", id).param("cursor", "not-a-real-cursor"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_CURSOR"));
    }

    // --- Unknown card 404s across every card-scoped endpoint -----------------------------------

    @Test
    void unknownCard_returns404OnMarketGradesAndPrices() throws Exception {
        UUID missing = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/cards/{id}/market", missing))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("CARD_NOT_FOUND"));
        mockMvc.perform(get("/api/v1/cards/{id}/grades", missing).param("days", "90"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("CARD_NOT_FOUND"));
        mockMvc.perform(get("/api/v1/cards/{id}/prices", missing).param("days", "90"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("CARD_NOT_FOUND"));
    }
}

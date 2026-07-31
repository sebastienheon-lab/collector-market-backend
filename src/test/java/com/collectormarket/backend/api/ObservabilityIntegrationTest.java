package com.collectormarket.backend.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;

import com.collectormarket.backend.api.config.CacheConfig;

/**
 * M7 DoD: Micrometer metrics are visible at {@code /actuator/metrics}, cache hits/misses are
 * observable, and {@code /actuator/health} answers (with the eBay component) without any live eBay
 * call. Runs against the shared Testcontainers Postgres.
 */
class ObservabilityIntegrationTest extends ApiIntegrationTestBase {

    @Autowired
    private CacheManager cacheManager;

    @Test
    void cardDetailIsCached_secondCallHitsCache() throws Exception {
        UUID id = insertCard("Cache Player " + UUID.randomUUID(), 2024, "Topps", "Chrome", "1");
        CaffeineCache cache = (CaffeineCache) cacheManager.getCache(CacheConfig.CARD_DETAIL);
        long hitsBefore = cache.getNativeCache().stats().hitCount();

        mockMvc.perform(get("/api/v1/cards/{id}", id)).andExpect(status().isOk()); // miss -> loads
        mockMvc.perform(get("/api/v1/cards/{id}", id)).andExpect(status().isOk()); // hit

        assertThat(cache.get(id)).isNotNull();
        assertThat(cache.getNativeCache().stats().hitCount()).isGreaterThan(hitsBefore);
    }

    @Test
    void cacheHitMissMetricIsExposed() throws Exception {
        // Warm the search cache so cache.gets has recorded activity, then read the metric.
        mockMvc.perform(get("/api/v1/cards/search").param("q", "metricprobe")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/cards/search").param("q", "metricprobe")).andExpect(status().isOk());

        mockMvc.perform(get("/actuator/metrics/cache.gets").param("tag", "cache:" + CacheConfig.SEARCH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("cache.gets"));
    }

    @Test
    void ebayQuotaRemainingGaugeIsExposed() throws Exception {
        mockMvc.perform(get("/actuator/metrics/ebay.quota.remaining"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("ebay.quota.remaining"))
                // Nothing has consumed quota in the test DB, so the full daily limit remains.
                .andExpect(jsonPath("$.measurements[0].value").value(5000.0));
    }

    @Test
    void cardsTrackedGaugeIsTaggedByTier() throws Exception {
        mockMvc.perform(get("/actuator/metrics/cards.tracked"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableTags[?(@.tag == 'tier')]").exists());
    }

    @Test
    void healthReportsEbayComponentWithoutLiveCall() throws Exception {
        // The eBay indicator reads cached state only; a fresh boot has no successful call yet but is
        // within the staleness window, so it reports UP and the overall status stays 200.
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components.ebay.status").exists())
                .andExpect(jsonPath("$.components.db.status").value("UP"));
    }
}

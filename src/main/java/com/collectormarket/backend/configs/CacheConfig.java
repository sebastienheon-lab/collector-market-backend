package com.collectormarket.backend.configs;

import java.time.Duration;
import java.util.List;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.github.benmanes.caffeine.cache.Caffeine;

/**
 * Caffeine caches (§9 M7). Each cache has its own TTL and size, so a {@link SimpleCacheManager}
 * over per-cache {@link CaffeineCache} instances is used rather than a single shared spec. Only
 * cheap, slow-changing reads are cached:
 * <ul>
 *   <li>{@link #SEARCH} - search results (5 min, 1000 entries): popular queries repeat;</li>
 *   <li>{@link #CARD_DETAIL} - immutable catalog rows (1 hour, 500 entries);</li>
 *   <li>{@link #APP_SETTING} - runtime tuning knobs (60 s per §7.12).</li>
 * </ul>
 * Deliberately NOT cached: the current-market view (live floor prices must never be stale) and
 * price history. {@code recordStats()} is on so Actuator exposes cache hit/miss at
 * {@code /actuator/metrics/cache.gets}. Because the manager is fixed, a {@code @Cacheable} naming
 * an unknown cache fails fast instead of silently creating an unbounded one.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    public static final String SEARCH = "search";
    public static final String CARD_DETAIL = "cardDetail";
    public static final String APP_SETTING = "appSetting";

    @Bean
    public CacheManager cacheManager() {
        SimpleCacheManager manager = new SimpleCacheManager();
        manager.setCaches(List.of(
                cache(SEARCH, Caffeine.newBuilder()
                        .expireAfterWrite(Duration.ofMinutes(5)).maximumSize(1000)),
                cache(CARD_DETAIL, Caffeine.newBuilder()
                        .expireAfterWrite(Duration.ofHours(1)).maximumSize(500)),
                cache(APP_SETTING, Caffeine.newBuilder()
                        .expireAfterWrite(Duration.ofSeconds(60)).maximumSize(500))));
        return manager;
    }

    private static CaffeineCache cache(String name, Caffeine<Object, Object> builder) {
        return new CaffeineCache(name, builder.recordStats().build());
    }
}

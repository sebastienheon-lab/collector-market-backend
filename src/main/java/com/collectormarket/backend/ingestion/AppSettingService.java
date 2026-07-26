package com.collectormarket.backend.ingestion;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Typed reader for {@code app_setting} (§7.12), the runtime-tunable operational knobs (aging
 * thresholds, poll cadence, events health-check horizon). Cached per-key with a 60s TTL so a
 * settings change propagates within a minute without hammering the DB on every read.
 */
@Component
public class AppSettingService {

    private static final Duration CACHE_TTL = Duration.ofSeconds(60);

    private final JdbcTemplate jdbcTemplate;
    private final ConcurrentHashMap<String, CachedSetting> cache = new ConcurrentHashMap<>();

    public AppSettingService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public int getInt(String key, int defaultValue) {
        String value = getRaw(key);
        return value != null ? Integer.parseInt(value) : defaultValue;
    }

    private String getRaw(String key) {
        Instant now = Instant.now();
        CachedSetting cached = cache.get(key);
        if (cached != null && now.isBefore(cached.expiresAt())) {
            return cached.value();
        }

        String value = jdbcTemplate.query(
                "SELECT value FROM app_setting WHERE key = ?",
                rs -> rs.next() ? rs.getString("value") : null,
                key);

        cache.put(key, new CachedSetting(value, now.plus(CACHE_TTL)));
        return value;
    }

    private record CachedSetting(String value, Instant expiresAt) {
    }
}

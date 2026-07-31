package com.collectormarket.backend.ingestion;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.collectormarket.backend.api.config.CacheConfig;

/**
 * Raw {@code app_setting} reads, cached in the {@code appSetting} Caffeine cache (60 s TTL, §7.12).
 * <p>
 * Split out from {@link AppSettingService} on purpose: {@code @Cacheable} only triggers through the
 * Spring proxy, so it must live on a bean the service calls, not a method the service invokes on
 * itself. A missing key returns {@code null}, and that null is cached too (via the cache
 * abstraction's null sentinel) - so absent settings don't hit the DB on every read either.
 */
@Component
public class AppSettingStore {

    private final JdbcTemplate jdbcTemplate;

    public AppSettingStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Cacheable(CacheConfig.APP_SETTING)
    public String getRaw(String key) {
        return jdbcTemplate.query(
                "SELECT value FROM app_setting WHERE key = ?",
                rs -> rs.next() ? rs.getString("value") : null,
                key);
    }
}

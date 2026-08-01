package com.collectormarket.backend.ingestion;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import com.collectormarket.backend.api.config.CacheConfig;
import com.collectormarket.backend.domain.AppSetting;
import com.collectormarket.backend.domain.AppSettingRepository;

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

    private final AppSettingRepository appSettingRepository;

    public AppSettingStore(AppSettingRepository appSettingRepository) {
        this.appSettingRepository = appSettingRepository;
    }

    @Cacheable(CacheConfig.APP_SETTING)
    public String getRaw(String key) {
        return appSettingRepository.findById(key).map(AppSetting::getValue).orElse(null);
    }
}

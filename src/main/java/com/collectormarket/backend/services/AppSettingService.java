package com.collectormarket.backend.services;

import org.springframework.stereotype.Component;

import com.collectormarket.backend.ingestion.AppSettingStore;

/**
 * Typed reader for {@code app_setting} (§7.12), the runtime-tunable operational knobs (aging
 * thresholds, poll cadence, events health-check horizon). Reads go through {@link AppSettingStore},
 * whose {@code appSetting} Caffeine cache (60 s TTL) means a settings change propagates within a
 * minute without hammering the DB on every read.
 */
@Component
public class AppSettingService {

    private final AppSettingStore store;

    public AppSettingService(AppSettingStore store) {
        this.store = store;
    }

    public int getInt(String key, int defaultValue) {
        String value = store.getRaw(key);
        return value != null ? Integer.parseInt(value) : defaultValue;
    }
}

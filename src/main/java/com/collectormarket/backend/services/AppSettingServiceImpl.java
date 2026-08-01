package com.collectormarket.backend.services;

import org.springframework.stereotype.Component;

import com.collectormarket.backend.ingestion.AppSettingStore;

/**
 * Default {@link AppSettingService}. Reads go through {@link AppSettingStore}, whose
 * {@code appSetting} Caffeine cache (60 s TTL) means a settings change propagates within a minute
 * without hammering the DB on every read.
 */
@Component
public class AppSettingServiceImpl implements AppSettingService {

    private final AppSettingStore store;

    public AppSettingServiceImpl(AppSettingStore store) {
        this.store = store;
    }

    @Override
    public int getInt(String key, int defaultValue) {
        String value = store.getRaw(key);
        return value != null ? Integer.parseInt(value) : defaultValue;
    }
}

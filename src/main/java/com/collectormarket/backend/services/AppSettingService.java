package com.collectormarket.backend.services;

/**
 * Typed reader for {@code app_setting} (§7.12), the runtime-tunable operational knobs (aging
 * thresholds, poll cadence, events health-check horizon).
 */
public interface AppSettingService {

    /** Returns the int-valued setting for {@code key}, or {@code defaultValue} if it's absent. */
    int getInt(String key, int defaultValue);
}

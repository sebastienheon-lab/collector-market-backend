package com.collectormarket.backend.api;

import java.util.Set;

import com.collectormarket.backend.api.error.InvalidDaysException;

/**
 * The {@code days} filter (F-05) is restricted to a fixed set of windows. Kept as an int on the
 * wire (it round-trips into the response as {@code timeRangeDays}), validated here rather than as
 * an enum so the JSON value stays a plain number.
 */
public final class TimeRangeDays {

    /** Advertised on the endpoints via {@code @Schema(allowableValues=...)}. */
    public static final Set<Integer> ALLOWED = Set.of(30, 90, 180, 365);

    private TimeRangeDays() {
    }

    /** Returns {@code days} if valid; otherwise throws {@link InvalidDaysException} (400). */
    public static int validate(int days) {
        if (!ALLOWED.contains(days)) {
            throw new InvalidDaysException(days);
        }
        return days;
    }
}

package com.collectormarket.backend.ebay;

import java.time.LocalDate;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Persisted daily call budget guard (M3), backed by {@code api_call_counter} (V010).
 * <p>
 * The counter is incremented on outbound send. If reserving a slot would exceed the daily
 * limit, the increment is undone immediately since the request never left - it is never
 * decremented after that point, regardless of whether the subsequent HTTP call succeeds,
 * because a call that reaches eBay counts against the real quota either way.
 */
@Component
public class ApiCallCounter {

    private final JdbcTemplate jdbcTemplate;

    public ApiCallCounter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean reserveCallSlot(String apiName, int dailyLimit) {
        LocalDate today = LocalDate.now();
        Integer newCount = jdbcTemplate.queryForObject("""
                INSERT INTO api_call_counter (call_date, api_name, call_count)
                VALUES (?, ?, 1)
                ON CONFLICT (call_date, api_name) DO UPDATE SET call_count = api_call_counter.call_count + 1
                RETURNING call_count
                """, Integer.class, today, apiName);

        if (newCount != null && newCount > dailyLimit) {
            jdbcTemplate.update("""
                    UPDATE api_call_counter SET call_count = call_count - 1
                    WHERE call_date = ? AND api_name = ?
                    """, today, apiName);
            return false;
        }
        return true;
    }

    public int currentCount(String apiName) {
        LocalDate today = LocalDate.now();
        Integer count = jdbcTemplate.queryForObject(
                "SELECT call_count FROM api_call_counter WHERE call_date = ? AND api_name = ?",
                Integer.class, today, apiName);
        return count != null ? count : 0;
    }

    /** Total calls made today across every api_name - backs the {@code ebay.quota.remaining} gauge. */
    public int totalCountToday() {
        Integer sum = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(call_count), 0) FROM api_call_counter WHERE call_date = ?",
                Integer.class, LocalDate.now());
        return sum != null ? sum : 0;
    }
}

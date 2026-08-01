package com.collectormarket.backend.ebay;

import java.time.LocalDate;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.collectormarket.backend.domain.ApiCallCountId;
import com.collectormarket.backend.domain.ApiCallCountRepository;

/**
 * Persisted daily call budget guard (M3), backed by {@code api_call_counter} (V010) via
 * {@link ApiCallCountRepository}.
 * <p>
 * The counter is incremented on outbound send by an atomic upsert (never a read-modify-write). If
 * reserving a slot would exceed the daily limit, the increment is undone immediately since the
 * request never left - it is never decremented after that point, regardless of whether the
 * subsequent HTTP call succeeds, because a call that reaches eBay counts against the real quota
 * either way. {@code reserveCallSlot} is transactional so the increment and its conditional
 * roll-back commit together.
 */
@Component
public class ApiCallCounter {

    private final ApiCallCountRepository apiCallCountRepository;

    public ApiCallCounter(ApiCallCountRepository apiCallCountRepository) {
        this.apiCallCountRepository = apiCallCountRepository;
    }

    @Transactional
    public boolean reserveCallSlot(String apiName, int dailyLimit) {
        LocalDate today = LocalDate.now();
        int newCount = apiCallCountRepository.incrementAndGet(today, apiName);

        if (newCount > dailyLimit) {
            apiCallCountRepository.decrement(today, apiName);
            return false;
        }
        return true;
    }

    public int currentCount(String apiName) {
        return apiCallCountRepository.findById(new ApiCallCountId(LocalDate.now(), apiName))
                .map(count -> count.getCallCount())
                .orElse(0);
    }

    /** Total calls made today across every api_name - backs the {@code ebay.quota.remaining} gauge. */
    public int totalCountToday() {
        return (int) apiCallCountRepository.totalCountOn(LocalDate.now());
    }
}

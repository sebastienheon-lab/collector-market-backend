package com.collectormarket.backend.dto;

import java.util.List;
import java.util.UUID;

/** Grade premium spread: average sale price per grade level (§8.5, F-19 groundwork). */
public record GradeBreakdownResponse(
        UUID cardId,
        int timeRangeDays,
        List<GradeAverage> grades) {
}

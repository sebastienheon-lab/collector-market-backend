package com.collectormarket.backend.services;

import java.util.UUID;

import com.collectormarket.backend.dto.GradeBreakdownResponse;

/** Grade premium spread (§8.5): average sale price per grade over the window. */
public interface GradeBreakdownService {

    GradeBreakdownResponse breakdown(UUID cardId, int days);
}

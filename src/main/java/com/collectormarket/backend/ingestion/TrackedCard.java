package com.collectormarket.backend.ingestion;

import java.time.Instant;
import java.util.UUID;

public record TrackedCard(
        UUID cardId,
        CardTier tier,
        PollCadence pollCadence,
        Instant lastPolledAt,
        Instant lastEngagementAt) {
}

package com.collectormarket.backend.ingestion;

/** card_tracking.tier (§7.10). Never "PAUSED" - that's a {@link PollCadence} value, not a tier. */
public enum CardTier {
    SEED,
    WATCHLISTED,
    SEARCHED,
    DECAYED
}

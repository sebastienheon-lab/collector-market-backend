package com.collectormarket.backend.ebay;

import java.time.Duration;
import java.time.Instant;

record CachedToken(String accessToken, Instant expiresAt) {

    boolean isUsable(Instant now, Duration refreshBuffer) {
        return now.isBefore(expiresAt.minus(refreshBuffer));
    }
}

package com.collectormarket.backend.dto;

import java.util.UUID;

/** Canonical card catalog detail (§8.4). {@code sport} is the human-readable {@code sport.code}. */
public record CardDetail(
        UUID id,
        String playerName,
        int year,
        String brand,
        String setName,
        String cardNumber,
        String sport,
        boolean isRookie,
        String parallel,
        Integer printRun) {
}

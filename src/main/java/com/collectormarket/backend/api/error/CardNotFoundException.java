package com.collectormarket.backend.api.error;

import java.util.UUID;

import org.springframework.http.HttpStatus;

/** Thrown when an endpoint is asked for a {@code cardId} that has no row in {@code card}. */
public class CardNotFoundException extends ApiException {

    public CardNotFoundException(UUID cardId) {
        super(HttpStatus.NOT_FOUND, ErrorCode.CARD_NOT_FOUND, "No card found with id " + cardId);
    }
}

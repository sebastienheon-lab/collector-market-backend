package com.collectormarket.backend.api.error;

import org.springframework.http.HttpStatus;

/** Thrown when the search {@code sport} parameter is not a known {@code sport.code}. */
public class InvalidSportException extends ApiException {

    public InvalidSportException(String sport) {
        super(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_SPORT, "Unknown sport code: " + sport);
    }
}

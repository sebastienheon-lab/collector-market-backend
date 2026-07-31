package com.collectormarket.backend.api.error;

import org.springframework.http.HttpStatus;

/** Thrown when a supplied price-history {@code cursor} is malformed or not decodable. */
public class InvalidCursorException extends ApiException {

    public InvalidCursorException() {
        super(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_CURSOR, "Malformed pagination cursor");
    }
}

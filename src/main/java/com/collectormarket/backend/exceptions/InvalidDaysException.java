package com.collectormarket.backend.exceptions;

import com.collectormarket.backend.api.error.ErrorCode;

import org.springframework.http.HttpStatus;

/** Thrown when {@code days} is outside the enum-validated set {30, 90, 180, 365}. */
public class InvalidDaysException extends ApiException {

    public InvalidDaysException(int days) {
        super(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_DAYS,
                "days must be one of 30, 90, 180, 365 (was " + days + ")");
    }
}

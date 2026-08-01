package com.collectormarket.backend.exceptions;

import com.collectormarket.backend.api.error.ErrorCode;

import org.springframework.http.HttpStatus;

/**
 * Base for the API's client-facing errors. Carries the HTTP status and the stable
 * {@link ErrorCode} so {@link ApiExceptionHandler} can render a uniform problem+json body without
 * a per-type mapping.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final ErrorCode errorCode;

    protected ApiException(HttpStatus status, ErrorCode errorCode, String message) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }

    public HttpStatus status() {
        return status;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }
}

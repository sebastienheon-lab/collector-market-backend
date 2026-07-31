package com.collectormarket.backend.api.error;

/**
 * Stable machine-readable error identifiers surfaced in the {@code errorCode} extension of every
 * RFC 7807 problem response. The frontend switches on these, so treat them as part of the API
 * contract - rename with care.
 */
public enum ErrorCode {
    CARD_NOT_FOUND,
    INVALID_GRADE,
    INVALID_DAYS,
    INVALID_SPORT,
    INVALID_CURSOR,
    VALIDATION_ERROR,
    INTERNAL_ERROR
}

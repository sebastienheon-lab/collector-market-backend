package com.collectormarket.backend.exceptions;

/** Retryable eBay API error (429 rate-limited or 5xx server error) - drives the backoff retry. */
public class EbayTransientApiException extends RuntimeException {

    public EbayTransientApiException(String message) {
        super(message);
    }
}

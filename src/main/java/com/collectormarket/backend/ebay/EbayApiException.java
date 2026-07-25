package com.collectormarket.backend.ebay;

/** Non-retryable eBay API error (4xx other than 429). */
public class EbayApiException extends RuntimeException {

    public EbayApiException(String message) {
        super(message);
    }
}

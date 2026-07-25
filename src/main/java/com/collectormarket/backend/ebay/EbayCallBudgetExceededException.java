package com.collectormarket.backend.ebay;

/** Thrown when a call is blocked before ever being sent because it would exceed the OQ-1 daily quota. */
public class EbayCallBudgetExceededException extends RuntimeException {

    public EbayCallBudgetExceededException(String message) {
        super(message);
    }
}

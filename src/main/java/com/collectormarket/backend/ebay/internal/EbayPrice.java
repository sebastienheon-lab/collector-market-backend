package com.collectormarket.backend.ebay.internal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record EbayPrice(String value, String currency) {
}

package com.collectormarket.backend.ebay.internal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Raw shape of eBay's OAuth2 client-credentials token response. Internal - never leaves the ebay package. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EbayTokenResponse(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("expires_in") long expiresIn,
        @JsonProperty("token_type") String tokenType) {
}

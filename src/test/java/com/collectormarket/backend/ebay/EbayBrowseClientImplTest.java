package com.collectormarket.backend.ebay;

import com.collectormarket.backend.properties.EbayProperties;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;

import com.collectormarket.backend.dto.ItemDetail;
import com.collectormarket.backend.dto.ItemSearchResult;
import com.collectormarket.backend.dto.ListingFormat;
import com.collectormarket.backend.observability.EbayCallState;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import reactor.test.StepVerifier;

/**
 * Fixture-based tests against a local WireMock server standing in for eBay's sandbox - no live
 * quota is burned. See {@code EbayBrowseClientLiveSmokeTest} for the real-API DoD check.
 */
@ExtendWith(MockitoExtension.class)
class EbayBrowseClientImplTest {

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(options().dynamicPort())
            .build();

    @Mock
    private ApiCallCounter callCounter;

    private EbayBrowseClientImpl client;

    @BeforeEach
    void setUp() {
        String baseUrl = wireMock.baseUrl();
        EbayProperties properties = new EbayProperties(
                "test-client-id",
                "test-client-secret",
                baseUrl + "/identity/v1/oauth2/token",
                baseUrl,
                5000,
                5000,
                10000,
                new EbayProperties.Retry(3, 10, 100),
                new EbayProperties.Category("261328"));

        WebClient webClient = WebClient.builder().baseUrl(baseUrl).build();
        EbayOAuthTokenProvider tokenProvider = new EbayOAuthTokenProvider(WebClient.builder(), properties);
        client = new EbayBrowseClientImpl(webClient, tokenProvider, callCounter, properties,
                new SimpleMeterRegistry(), new EbayCallState());
    }

    @Test
    void searchItemsMapsEbayResponseToAppDto() {
        when(callCounter.reserveCallSlot(anyString(), anyInt())).thenReturn(true);
        stubTokenEndpoint();
        wireMock.stubFor(get(urlPathEqualTo("/buy/browse/v1/item_summary/search"))
                .withQueryParam("q", equalTo("2018 Topps Ohtani"))
                .withQueryParam("category_ids", equalTo("261328"))
                .withHeader("Authorization", equalTo("Bearer test-access-token"))
                .willReturn(okJson("""
                        {
                          "itemSummaries": [
                            {
                              "itemId": "v1|123456789|0",
                              "title": "2018 Topps Update Shohei Ohtani RC #US1 PSA 10",
                              "price": { "value": "245.00", "currency": "USD" },
                              "condition": "Graded",
                              "itemWebUrl": "https://www.sandbox.ebay.com/itm/123456789",
                              "seller": { "username": "cardseller123" },
                              "buyingOptions": ["FIXED_PRICE"]
                            }
                          ],
                          "total": 1,
                          "limit": 50,
                          "offset": 0
                        }
                        """)));

        ItemSearchResult result = client.searchItems("2018 Topps Ohtani", "261328").block();

        assertThat(result).isNotNull();
        assertThat(result.total()).isEqualTo(1);
        assertThat(result.items()).hasSize(1);
        var item = result.items().get(0);
        assertThat(item.itemId()).isEqualTo("v1|123456789|0");
        assertThat(item.price()).isEqualByComparingTo(new BigDecimal("245.00"));
        assertThat(item.currency()).isEqualTo("USD");
        assertThat(item.sellerUsername()).isEqualTo("cardseller123");
        assertThat(item.listingFormat()).isEqualTo(ListingFormat.FIXED_PRICE);
    }

    @Test
    void getItemMapsEstimatedSoldQuantity() {
        when(callCounter.reserveCallSlot(anyString(), anyInt())).thenReturn(true);
        stubTokenEndpoint();
        wireMock.stubFor(get(urlPathEqualTo("/buy/browse/v1/item/v1%7C123456789%7C0"))
                .willReturn(okJson("""
                        {
                          "itemId": "v1|123456789|0",
                          "title": "2018 Topps Update Shohei Ohtani RC #US1 PSA 10",
                          "price": { "value": "245.00", "currency": "USD" },
                          "condition": "Graded",
                          "itemWebUrl": "https://www.sandbox.ebay.com/itm/123456789",
                          "seller": { "username": "cardseller123" },
                          "buyingOptions": ["FIXED_PRICE"],
                          "estimatedAvailabilities": [
                            {
                              "estimatedAvailabilityStatus": "MORE_THAN_10",
                              "estimatedAvailableQuantity": 3,
                              "estimatedSoldQuantity": 12
                            }
                          ]
                        }
                        """)));

        ItemDetail detail = client.getItem("v1|123456789|0").block();

        assertThat(detail).isNotNull();
        assertThat(detail.estimatedAvailableQuantity()).isEqualTo(3);
        assertThat(detail.estimatedSoldQuantity()).isEqualTo(12);
    }

    @Test
    void retriesOnServerErrorThenSucceeds() {
        when(callCounter.reserveCallSlot(anyString(), anyInt())).thenReturn(true);
        stubTokenEndpoint();
        wireMock.stubFor(get(urlPathEqualTo("/buy/browse/v1/item_summary/search"))
                .inScenario("retry")
                .whenScenarioStateIs(STARTED)
                .willReturn(aResponse().withStatus(503))
                .willSetStateTo("retried"));
        wireMock.stubFor(get(urlPathEqualTo("/buy/browse/v1/item_summary/search"))
                .inScenario("retry")
                .whenScenarioStateIs("retried")
                .willReturn(okJson("""
                        { "itemSummaries": [], "total": 0, "limit": 50, "offset": 0 }
                        """)));

        ItemSearchResult result = client.searchItems("query", "261328").block();

        assertThat(result).isNotNull();
        assertThat(result.total()).isEqualTo(0);
    }

    @Test
    void doesNotSendRequestWhenDailyBudgetExceeded() {
        when(callCounter.reserveCallSlot(anyString(), anyInt())).thenReturn(false);

        StepVerifier.create(client.searchItems("query", "261328"))
                .expectError(EbayCallBudgetExceededException.class)
                .verify();

        wireMock.verify(0, getRequestedFor(urlPathEqualTo("/buy/browse/v1/item_summary/search")));
    }

    private void stubTokenEndpoint() {
        wireMock.stubFor(post(urlPathEqualTo("/identity/v1/oauth2/token"))
                .willReturn(okJson("""
                        {
                          "access_token": "test-access-token",
                          "expires_in": 7200,
                          "token_type": "Application Access Token"
                        }
                        """)));
    }
}

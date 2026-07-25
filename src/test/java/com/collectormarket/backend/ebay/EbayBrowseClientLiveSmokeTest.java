package com.collectormarket.backend.ebay;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.collectormarket.backend.ebay.dto.ItemSearchResult;

/**
 * M3 DoD: {@code EbayBrowseClient.searchItems("2018 Topps Ohtani", tradingCardsCategoryId)}
 * against the real eBay sandbox. Tagged {@code live} and excluded from the default
 * {@code mvn test}/CI run (pom.xml surefire {@code excludedGroups}) - it burns real OQ-1 quota
 * and needs network access plus valid sandbox credentials in application-credentials.yml.
 * <p>
 * Run explicitly with: {@code mvn test -Dtest=EbayBrowseClientLiveSmokeTest -DexcludedGroups=}
 */
@Tag("live")
@SpringBootTest
class EbayBrowseClientLiveSmokeTest {

    @Autowired
    private EbayBrowseClient ebayBrowseClient;

    @Autowired
    private EbayProperties ebayProperties;

    @Test
    void searchItemsReturnsParsedDtosFromLiveApi() {
        ItemSearchResult result = ebayBrowseClient
                .searchItems("2018 Topps Ohtani", ebayProperties.category().tradingCardSingles())
                .block();

        assertThat(result).isNotNull();
        assertThat(result.items()).isNotNull();
        // Sandbox inventory is sparse/synthetic - this asserts the round trip and mapping
        // succeeded against the live API, not that specific Ohtani listings exist.
    }
}

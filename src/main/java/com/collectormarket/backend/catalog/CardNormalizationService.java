package com.collectormarket.backend.catalog;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;

/**
 * Entry point for turning a raw eBay listing title into a canonical card id (§10 Phase 1).
 * Parses the title, then attempts an exact match against the seeded catalog; anything that
 * doesn't resolve - including titles that clearly reference a non-base variant (parallel,
 * autograph, relic, etc.) - is logged to {@code unmatched_listing} for later triage rather than
 * dropped.
 */
@Component
public class CardNormalizationService {

    private final TitleParser titleParser;
    private final CardCatalogLookup catalogLookup;
    private final UnmatchedListingRecorder unmatchedListingRecorder;

    public CardNormalizationService(
            TitleParser titleParser, CardCatalogLookup catalogLookup, UnmatchedListingRecorder unmatchedListingRecorder) {
        this.titleParser = titleParser;
        this.catalogLookup = catalogLookup;
        this.unmatchedListingRecorder = unmatchedListingRecorder;
    }

    public Optional<UUID> normalize(String rawTitle) {
        Set<String> knownPlayerNames = catalogLookup.allPlayerNames();
        ParsedTitle parsed = titleParser.parse(rawTitle, knownPlayerNames);

        Optional<UUID> matchedCardId = parsed.variantDetected()
                ? Optional.empty()
                : catalogLookup.findExactMatch(parsed);

        if (matchedCardId.isEmpty()) {
            unmatchedListingRecorder.record(rawTitle, parsed);
        }

        return matchedCardId;
    }
}

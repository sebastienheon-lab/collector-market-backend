package com.collectormarket.backend.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.collectormarket.backend.domain.CardRepository;
import com.collectormarket.backend.domain.UnmatchedListingRepository;
import com.collectormarket.backend.services.CardNormalizationService;

/**
 * M4 DoD: a batch of 50 representative eBay titles yields canonical card IDs for the seeded
 * scope and logged unmatched rows for the rest. Runs against dev-postgres with the real seeded
 * catalog (CardSeedLoader has already run by the time @SpringBootTest boots the context) and
 * rolls back afterward so it doesn't pollute unmatched_listing between runs.
 * <p>
 * Titles are a mix of real eBay listing titles pulled during M4 research and representative
 * titles covering the rest of the seeded catalog's sports/years/grades.
 */
@SpringBootTest
@Transactional
class CardNormalizationServiceIntegrationTest {

    // player, year, brand - used to independently look up the expected card id in the DB,
    // rather than just asserting normalize() returned "some" id.
    private record ExpectedMatch(String title, String player, int year, String brand) {
    }

    private static final List<ExpectedMatch> SHOULD_MATCH = List.of(
            new ExpectedMatch("Corbin Carroll 2023 Topps Chrome #95 (RC) PSA 10", "Corbin Carroll", 2023, "Topps"),
            new ExpectedMatch("2023 Topps Chrome Gunnar Henderson RC PSA 9 Orioles", "Gunnar Henderson", 2023, "Topps"),
            new ExpectedMatch("2023 Topps Chrome Anthony Volpe Gem Mint 10 RC Yankees", "Anthony Volpe", 2023, "Topps"),
            new ExpectedMatch("2023 Topps Chrome Elly De La Cruz RC CGC 9 Reds", "Elly De La Cruz", 2023, "Topps"),
            new ExpectedMatch("2024 Topps Chrome Paul Skenes RC BGS 9.5 Pirates", "Paul Skenes", 2024, "Topps"),
            new ExpectedMatch("2024 Topps Chrome Jackson Merrill Rookie SGC 10 Padres", "Jackson Merrill", 2024, "Topps"),
            new ExpectedMatch("2024 Topps Chrome Jackson Holliday RC PSA 10 Orioles", "Jackson Holliday", 2024, "Topps"),
            new ExpectedMatch("2025 Topps Chrome Nick Kurtz Rookie Card RC Athletics", "Nick Kurtz", 2025, "Topps"),
            new ExpectedMatch("2025 Topps Chrome Roki Sasaki RC PSA 10 Dodgers", "Roki Sasaki", 2025, "Topps"),
            new ExpectedMatch(
                    "2023-24 Panini NBA Prizm Victor Wembanyama Base Rookie Card SA Spurs #136",
                    "Victor Wembanyama", 2023, "Panini"),
            new ExpectedMatch(
                    "2023-24 Panini Prizm Scoot Henderson RC PSA 9 Trail Blazers", "Scoot Henderson", 2023, "Panini"),
            new ExpectedMatch(
                    "2023-24 Panini Prizm Brandon Miller Rookie Card Hornets RC", "Brandon Miller", 2023, "Panini"),
            new ExpectedMatch(
                    "2024-25 Panini Prizm Zaccharie Risacher RC Hawks PSA 10", "Zaccharie Risacher", 2024, "Panini"),
            new ExpectedMatch(
                    "2024-25 Panini Prizm Reed Sheppard Rookie Card Rockets BGS 9.5", "Reed Sheppard", 2024, "Panini"),
            new ExpectedMatch(
                    "2025-26 Topps Basketball Cooper Flagg #201 Rookie RC Mavericks", "Cooper Flagg", 2025, "Topps"),
            new ExpectedMatch("2025-26 Topps Basketball Dylan Harper RC Spurs", "Dylan Harper", 2025, "Topps"),
            new ExpectedMatch(
                    "2023 Panini Prizm Football CJ STROUD RC Rookie Card Base #339 Houston Texans",
                    "C.J. Stroud", 2023, "Panini"),
            new ExpectedMatch("2023 Panini Prizm Bryce Young RC PSA 10 Panthers", "Bryce Young", 2023, "Panini"),
            new ExpectedMatch(
                    "2023 Panini Prizm Bijan Robinson Rookie Card RC Falcons", "Bijan Robinson", 2023, "Panini"),
            new ExpectedMatch("2024 Panini Prizm Jayden Daniels RC BGS 9.5 Commanders", "Jayden Daniels", 2024, "Panini"),
            new ExpectedMatch(
                    "2024 Panini Prizm Caleb Williams Rookie Card Bears SGC 10", "Caleb Williams", 2024, "Panini"),
            new ExpectedMatch("2025 Panini Prizm Travis Hunter #301 RC Jaguars PSA 10", "Travis Hunter", 2025, "Panini"),
            new ExpectedMatch(
                    "2025 Panini Prizm Shedeur Sanders #302 Rookie Card Browns", "Shedeur Sanders", 2025, "Panini"),
            new ExpectedMatch("2025 Panini Prizm Ashton Jeanty RC Raiders CGC 9.5", "Ashton Jeanty", 2025, "Panini"),
            new ExpectedMatch(
                    "2023-24 Upper Deck Series 2 Connor Bedard Young Guns Rookie Blackhawks #451 RC",
                    "Connor Bedard", 2023, "Upper Deck"),
            new ExpectedMatch(
                    "2023-24 Upper Deck Young Guns Adam Fantilli RC Blue Jackets PSA 9", "Adam Fantilli", 2023, "Upper Deck"),
            new ExpectedMatch(
                    "2024-25 Upper Deck Young Guns Macklin Celebrini SGC 10 Sharks Rookie",
                    "Macklin Celebrini", 2024, "Upper Deck"),
            new ExpectedMatch(
                    "2024-25 Upper Deck Young Guns Cutter Gauthier #212 RC Ducks", "Cutter Gauthier", 2024, "Upper Deck"),
            new ExpectedMatch(
                    "2024-25 Upper Deck Young Guns Logan Stankoven #244 Rookie Stars",
                    "Logan Stankoven", 2024, "Upper Deck"),
            new ExpectedMatch(
                    "2025-26 Upper Deck Young Guns Ivan Demidov RC Canadiens PSA 10", "Ivan Demidov", 2025, "Upper Deck"));

    private static final List<String> SHOULD_NOT_MATCH = List.of(
            // Real listing: autograph variant of a seeded base card
            "2023 Topps Chrome Update Corbin Carroll Rookie Auto PSA 10 RC Diamondbacks ROTY",
            // Real listing: insert, different card number than the base (#TT-9 vs #95)
            "PSA 10 2023 Topps Chrome Corbin Carroll Topps In Technicolor Rookie RC #TT-9",
            // Real listing: parallel of a seeded base card, same card number as base
            "2023-24 Panini Prizm - Victor Wembanyama #136 Silver Prizm (RC)",
            // Real listing: another real parallel color name
            "2023-24 Panini Prizm Victor Wembanyama Green Prizm Rookie #136 PSA 10 ROY",
            "C.J. Stroud 2023 Panini Prizm Orange Lazer Prizm Rookie RC Card #339 Texans",
            "2023-24 Upper Deck Connor Bedard #451 Young Guns Outburst Silver Rookie PSA 9",
            // Bowman Chrome isn't in the seed for this player/year (seed has Holliday under 2024 Topps Chrome)
            "2023 Bowman Chrome Jackson Holliday 1st Bowman Auto",
            // Player not in the seeded catalog at all
            "2022 Topps Chrome Julio Rodriguez RC PSA 10 Mariners",
            "2023 Topps Chrome Mike Trout RC PSA 10 Angels",
            "1998 Upper Deck Young Guns Some Guy RC",
            // Explicit refractor/gold/relic/patch/jersey variant keywords
            "2023 Topps Chrome Corbin Carroll RC #95 Refractor PSA 10",
            "2024 Panini Prizm Marvin Harrison Jr Gold Prizm RC Cardinals",
            "2024 Topps Chrome Wyatt Langford Relic Patch Auto RC Rangers",
            "2025 Panini Prizm Travis Hunter Jersey Patch RC Jaguars",
            "2025-26 Topps Basketball Cooper Flagg Autographed Jersey Number RC",
            // Explicit "Parallel" keyword
            "2024-25 Upper Deck Young Guns Macklin Celebrini Exclusives Parallel RC",
            // Real listing: different insert card number than the base (#C382 vs #451), no variant
            // keyword present - this one has to be caught by the card-number mismatch, not the parser
            "2023-24 Upper Deck Extended Series - Ud Canvas Young Guns Connor Bedard #C382 (RC)",
            // Real listing: different card number than the base (#PB-6 vs #339)
            "2023 Panini Prizm C.J. Stroud Prizm Break Rookie RC #PB-6 Houston Texans",
            // No player name at all
            "Brand new sealed wax box 2024 Panini Prizm Football Hobby",
            // Garbage / unrelated
            "Vintage 1987 Topps Don Mattingly Card Near Mint");

    @Autowired
    private CardNormalizationService normalizationService;

    @Autowired
    private CardRepository cardRepository;

    @Autowired
    private UnmatchedListingRepository unmatchedListingRepository;

    @Test
    void fiftyTitleBatch_matchesSeededCards_andLogsTheRestAsUnmatched() {
        assertThat(SHOULD_MATCH.size() + SHOULD_NOT_MATCH.size()).isEqualTo(50);

        for (ExpectedMatch expected : SHOULD_MATCH) {
            Optional<UUID> result = normalizationService.normalize(expected.title());

            assertThat(result)
                    .as("expected a match for: %s", expected.title())
                    .isPresent();
            assertThat(result.get())
                    .as("matched the wrong card for: %s", expected.title())
                    .isEqualTo(expectedCardId(expected));
        }

        for (String title : SHOULD_NOT_MATCH) {
            Optional<UUID> result = normalizationService.normalize(title);

            assertThat(result).as("expected no match for: %s", title).isEmpty();

            // Read via the repository (not raw SQL): a JPQL count auto-flushes the pending JPA
            // insert first, so it's visible within this @Transactional test's session.
            long unmatchedRows = unmatchedListingRepository.countByRawTitle(title);
            assertThat(unmatchedRows).as("expected an unmatched_listing row for: %s", title).isEqualTo(1);
        }
    }

    private UUID expectedCardId(ExpectedMatch expected) {
        List<UUID> ids = cardRepository.findExactMatchIds(
                expected.player(), (short) expected.year(), expected.brand(), null, null);
        assertThat(ids).as("seed should hold exactly one card for: %s", expected.title()).hasSize(1);
        return ids.get(0);
    }
}

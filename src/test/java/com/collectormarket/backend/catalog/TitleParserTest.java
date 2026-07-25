package com.collectormarket.backend.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * Fixtures below are real eBay listing titles pulled during M4 research (search results for
 * Corbin Carroll / Victor Wembanyama / C.J. Stroud / Connor Bedard listings), plus representative
 * titles covering the other grade/brand/edge cases. This tests parsing in isolation - whether a
 * parsed title actually resolves to a catalog card is CardNormalizationServiceIntegrationTest's job.
 */
class TitleParserTest {

    private static final Set<String> KNOWN_PLAYERS = Set.of(
            "Corbin Carroll", "Victor Wembanyama", "C.J. Stroud", "Connor Bedard",
            "Jayden Daniels", "Macklin Celebrini", "Elly De La Cruz", "Jackson Holliday",
            "Cooper Flagg", "Anthony Volpe", "Bijan Robinson");

    private final TitleParser parser = new TitleParser();

    @Test
    void parsesRealBaseCardListing_corbinCarroll() {
        ParsedTitle parsed = parser.parse("Corbin Carroll 2023 Topps Chrome #95 (RC) PSA 10", KNOWN_PLAYERS);

        assertThat(parsed.year()).isEqualTo(2023);
        assertThat(parsed.playerName()).isEqualTo("Corbin Carroll");
        assertThat(parsed.brand()).isEqualTo("Topps");
        assertThat(parsed.setName()).isEqualTo("Chrome");
        assertThat(parsed.cardNumber()).isEqualTo("95");
        assertThat(parsed.gradeSource()).isEqualTo("PSA");
        assertThat(parsed.gradeValue()).isEqualTo("10");
        assertThat(parsed.variantDetected()).isFalse();
    }

    @Test
    void parsesRealAutoListing_asVariant_corbinCarroll() {
        ParsedTitle parsed = parser.parse(
                "2023 Topps Chrome Update Corbin Carroll Rookie Auto PSA 10 RC Diamondbacks ROTY", KNOWN_PLAYERS);

        assertThat(parsed.year()).isEqualTo(2023);
        assertThat(parsed.playerName()).isEqualTo("Corbin Carroll");
        assertThat(parsed.brand()).isEqualTo("Topps");
        assertThat(parsed.setName()).isEqualTo("Chrome");
        assertThat(parsed.cardNumber()).isNull();
        assertThat(parsed.variantDetected()).isTrue();
    }

    @Test
    void parsesRealInsertListing_technicolor_corbinCarroll() {
        // Real title: an insert ("Topps In Technicolor"), not the base card - card number TT-9
        // differs from the base card's #95, which is what actually excludes it downstream.
        ParsedTitle parsed = parser.parse(
                "PSA 10 2023 Topps Chrome Corbin Carroll Topps In Technicolor Rookie RC #TT-9", KNOWN_PLAYERS);

        assertThat(parsed.playerName()).isEqualTo("Corbin Carroll");
        assertThat(parsed.cardNumber()).isEqualTo("TT-9");
        assertThat(parsed.gradeSource()).isEqualTo("PSA");
        assertThat(parsed.gradeValue()).isEqualTo("10");
    }

    @Test
    void parsesRealBaseCardListing_wembanyama() {
        ParsedTitle parsed = parser.parse(
                "2023-24 Panini NBA Prizm Victor Wembanyama Base Rookie Card SA Spurs #136", KNOWN_PLAYERS);

        assertThat(parsed.year()).isEqualTo(2023);
        assertThat(parsed.playerName()).isEqualTo("Victor Wembanyama");
        assertThat(parsed.brand()).isEqualTo("Panini");
        assertThat(parsed.setName()).isEqualTo("Prizm");
        assertThat(parsed.cardNumber()).isEqualTo("136");
        assertThat(parsed.variantDetected()).isFalse();
    }

    @Test
    void parsesRealParallelListing_asVariant_wembanyama() {
        // Real title: Silver Prizm parallel shares the base card's #136 - card number alone
        // can't disambiguate; the "silver" keyword is what has to catch this.
        ParsedTitle parsed = parser.parse(
                "2023-24 Panini Prizm - Victor Wembanyama #136 Silver Prizm (RC)", KNOWN_PLAYERS);

        assertThat(parsed.cardNumber()).isEqualTo("136");
        assertThat(parsed.variantDetected()).isTrue();
    }

    @Test
    void parsesRealBaseCardListing_stroud_periodsInsertedInTitleButNotInSource() {
        ParsedTitle parsed = parser.parse(
                "2023 Panini Prizm Football CJ STROUD RC Rookie Card Base #339 Houston Texans", KNOWN_PLAYERS);

        assertThat(parsed.year()).isEqualTo(2023);
        assertThat(parsed.playerName()).isEqualTo("C.J. Stroud");
        assertThat(parsed.brand()).isEqualTo("Panini");
        assertThat(parsed.setName()).isEqualTo("Prizm");
        assertThat(parsed.cardNumber()).isEqualTo("339");
        assertThat(parsed.variantDetected()).isFalse();
    }

    @Test
    void parsesRealParallelListing_asVariant_stroud() {
        ParsedTitle parsed = parser.parse(
                "C.J. Stroud 2023 Panini Prizm Orange Lazer Prizm Rookie RC Card #339 Texans", KNOWN_PLAYERS);

        assertThat(parsed.cardNumber()).isEqualTo("339");
        assertThat(parsed.variantDetected()).isTrue();
    }

    @Test
    void parsesRealBaseCardListing_bedard() {
        ParsedTitle parsed = parser.parse(
                "2023-24 Upper Deck Series 2 Connor Bedard Young Guns Rookie Blackhawks #451 RC", KNOWN_PLAYERS);

        assertThat(parsed.year()).isEqualTo(2023);
        assertThat(parsed.playerName()).isEqualTo("Connor Bedard");
        assertThat(parsed.brand()).isEqualTo("Upper Deck");
        assertThat(parsed.setName()).isEqualTo("Young Guns");
        assertThat(parsed.cardNumber()).isEqualTo("451");
        assertThat(parsed.variantDetected()).isFalse();
    }

    @Test
    void parsesRealParallelListing_asVariant_bedard() {
        ParsedTitle parsed = parser.parse(
                "2023-24 Upper Deck Connor Bedard #451 Young Guns Outburst Silver Rookie PSA 9", KNOWN_PLAYERS);

        assertThat(parsed.gradeSource()).isEqualTo("PSA");
        assertThat(parsed.gradeValue()).isEqualTo("9");
        assertThat(parsed.variantDetected()).isTrue();
    }

    @Test
    void recognizesBgsHalfGrade() {
        ParsedTitle parsed = parser.parse("2024 Panini Prizm Jayden Daniels RC BGS 9.5 Commanders", KNOWN_PLAYERS);

        assertThat(parsed.gradeSource()).isEqualTo("BGS");
        assertThat(parsed.gradeValue()).isEqualTo("9.5");
    }

    @Test
    void recognizesSgcGrade() {
        ParsedTitle parsed = parser.parse(
                "2024 Upper Deck Young Guns Macklin Celebrini SGC 10 Sharks Rookie", KNOWN_PLAYERS);

        assertThat(parsed.gradeSource()).isEqualTo("SGC");
        assertThat(parsed.gradeValue()).isEqualTo("10");
    }

    @Test
    void recognizesCgcGrade() {
        ParsedTitle parsed = parser.parse("2023 Topps Chrome Elly De La Cruz RC CGC 9 Reds", KNOWN_PLAYERS);

        assertThat(parsed.gradeSource()).isEqualTo("CGC");
        assertThat(parsed.gradeValue()).isEqualTo("9");
    }

    @Test
    void recognizesGemMintAsPsaTen() {
        ParsedTitle parsed = parser.parse("2023 Topps Chrome Anthony Volpe Gem Mint 10 RC Yankees", KNOWN_PLAYERS);

        assertThat(parsed.gradeSource()).isEqualTo("PSA");
        assertThat(parsed.gradeValue()).isEqualTo("10");
    }

    @Test
    void defaultsToRawWhenNoGraderMentioned() {
        ParsedTitle parsed = parser.parse("2023 Panini Prizm Bijan Robinson Rookie Card RC Falcons", KNOWN_PLAYERS);

        assertThat(parsed.gradeSource()).isEqualTo("RAW");
        assertThat(parsed.gradeValue()).isEqualTo("RAW");
    }

    @Test
    void detectsBowmanChromeDistinctFromToppsChrome() {
        ParsedTitle parsed = parser.parse("2023 Bowman Chrome Jackson Holliday 1st Bowman Auto", KNOWN_PLAYERS);

        assertThat(parsed.brand()).isEqualTo("Bowman");
        assertThat(parsed.setName()).isEqualTo("Chrome");
        assertThat(parsed.variantDetected()).isTrue();
    }

    @Test
    void usesSeasonFormatYearAsFirstYear() {
        ParsedTitle parsed = parser.parse(
                "2025-26 Topps Basketball Cooper Flagg #201 Rookie RC Mavericks", KNOWN_PLAYERS);

        assertThat(parsed.year()).isEqualTo(2025);
        assertThat(parsed.brand()).isEqualTo("Topps");
        assertThat(parsed.setName()).isEqualTo("Basketball");
        assertThat(parsed.cardNumber()).isEqualTo("201");
    }

    @Test
    void playerNameIsNullWhenNotInKnownCatalog() {
        ParsedTitle parsed = parser.parse("2023 Topps Chrome Some Unknown Prospect RC #50", KNOWN_PLAYERS);

        assertThat(parsed.playerName()).isNull();
    }
}

package com.collectormarket.backend.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Grade parser (§7.3): extracting the (gradeSource, gradeValue) pair from an eBay listing title.
 * Companion to {@link TitleParserTest} - this isolates {@link GradeNormalizer}'s regex behaviour,
 * including the subgrade and Gem-Mint edge cases and the ungraded fallback.
 */
class GradeNormalizerTest {

    @ParameterizedTest
    @CsvSource({
            // title,                                             expectedSource, expectedValue
            "'Corbin Carroll 2023 Topps Chrome #95 RC PSA 10',    PSA, 10",
            "'2018 Topps Update Shohei Ohtani RC BGS 9.5',        BGS, 9.5",
            "'2003 LeBron James Topps Chrome #111 SGC 8',         SGC, 8",
            "'Victor Wembanyama Prizm Silver CGC 9',              CGC, 9",
            "'Jordan Fleer #57 BGS 9.75 (quarter subgrade)',      BGS, 9.75",
    })
    void extractsGraderAndValue(String title, String expectedSource, String expectedValue) {
        GradeInfo info = GradeNormalizer.extract(title);

        assertThat(info.gradeSource()).isEqualTo(expectedSource);
        assertThat(info.gradeValue()).isEqualTo(expectedValue);
    }

    @ParameterizedTest
    @CsvSource({
            "'graded psa 10 gem mint', PSA, 10",   // lowercase grader token
            "'Bedard rookie Bgs 9.5',  BGS, 9.5",  // mixed case
    })
    void isCaseInsensitiveAndNormalisesGraderToUppercase(String title, String source, String value) {
        GradeInfo info = GradeNormalizer.extract(title);

        assertThat(info.gradeSource()).isEqualTo(source);
        assertThat(info.gradeValue()).isEqualTo(value);
    }

    @Test
    void treatsBareGemMintAsPsaGrade() {
        // "Gem Mint 10" with no explicit grader is conventionally PSA terminology.
        GradeInfo info = GradeNormalizer.extract("2020 Justin Herbert Prizm Gem Mint 10");

        assertThat(info.gradeSource()).isEqualTo("PSA");
        assertThat(info.gradeValue()).isEqualTo("10");
    }

    @Test
    void prefersExplicitGraderOverGemMintWording() {
        // An explicit grader token wins even when "Gem Mint" also appears in the title.
        GradeInfo info = GradeNormalizer.extract("2021 Trevor Lawrence BGS 9.5 Gem Mint");

        assertThat(info.gradeSource()).isEqualTo("BGS");
        assertThat(info.gradeValue()).isEqualTo("9.5");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "2023 Corbin Carroll Topps Chrome #95 RC",   // no grade token at all
            "Raw ungraded Wembanyama Prizm base",         // "Raw" wording, no grader
            "PSA authenticated sticker (no numeric grade)" // grader word but no number
    })
    void fallsBackToRawWhenNoGradePresent(String title) {
        assertThat(GradeNormalizer.extract(title)).isEqualTo(GradeInfo.RAW);
    }
}

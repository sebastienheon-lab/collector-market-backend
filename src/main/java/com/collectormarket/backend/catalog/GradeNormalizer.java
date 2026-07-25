package com.collectormarket.backend.catalog;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Recognizes PSA/BGS/SGC/CGC grades (incl. half/quarter subgrades) and the ungraded "raw" case. */
public final class GradeNormalizer {

    private static final Pattern GRADED_PATTERN =
            Pattern.compile("\\b(PSA|BGS|SGC|CGC)\\b\\s*(\\d{1,2}(?:\\.\\d{1,2})?)", Pattern.CASE_INSENSITIVE);

    // "Gem Mint 10" with no explicit grader prefix is conventionally PSA's own grade-10 terminology.
    private static final Pattern GEM_MINT_PATTERN =
            Pattern.compile("\\bGem\\s*Mint\\s*(\\d{1,2}(?:\\.\\d{1,2})?)", Pattern.CASE_INSENSITIVE);

    private GradeNormalizer() {
    }

    public static GradeInfo extract(String title) {
        Matcher graded = GRADED_PATTERN.matcher(title);
        if (graded.find()) {
            return new GradeInfo(graded.group(1).toUpperCase(), graded.group(2));
        }

        Matcher gemMint = GEM_MINT_PATTERN.matcher(title);
        if (gemMint.find()) {
            return new GradeInfo("PSA", gemMint.group(1));
        }

        return GradeInfo.RAW;
    }
}

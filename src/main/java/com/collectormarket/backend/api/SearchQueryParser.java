package com.collectormarket.backend.api;

import java.time.Year;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Splits a free-text search query into a player-name term (for trigram similarity) plus optional
 * exact filters for year and card number when they appear in the query. Mirrors
 * {@link com.collectormarket.backend.catalog.TitleParser}'s extraction rules: a plausible 4-digit
 * year, and a {@code #}-prefixed card number (stored bare, e.g. {@code #95} -&gt; {@code "95"}).
 */
public final class SearchQueryParser {

    private static final Pattern YEAR_PATTERN = Pattern.compile("\\b(19|20)\\d{2}\\b");
    private static final Pattern CARD_NUMBER_PATTERN = Pattern.compile("#([A-Za-z0-9-]+)");
    private static final int MIN_PLAUSIBLE_YEAR = 1980;

    private SearchQueryParser() {
    }

    public static ParsedQuery parse(String rawQuery) {
        String q = rawQuery == null ? "" : rawQuery;

        Integer year = extractYear(q);
        String cardNumber = extractCardNumber(q);

        // Strip the matched year/number tokens so they don't pollute the trigram player term.
        String remainder = q;
        if (cardNumber != null) {
            remainder = CARD_NUMBER_PATTERN.matcher(remainder).replaceAll(" ");
        }
        if (year != null) {
            remainder = remainder.replaceFirst("\\b" + year + "\\b", " ");
        }
        String playerTerm = remainder.trim().replaceAll("\\s+", " ");

        return new ParsedQuery(playerTerm.isBlank() ? null : playerTerm, year, cardNumber);
    }

    private static Integer extractYear(String q) {
        Matcher matcher = YEAR_PATTERN.matcher(q);
        int currentYearPlusOne = Year.now().getValue() + 1;
        while (matcher.find()) {
            int candidate = Integer.parseInt(matcher.group());
            if (candidate >= MIN_PLAUSIBLE_YEAR && candidate <= currentYearPlusOne) {
                return candidate;
            }
        }
        return null;
    }

    private static String extractCardNumber(String q) {
        Matcher matcher = CARD_NUMBER_PATTERN.matcher(q);
        return matcher.find() ? matcher.group(1) : null;
    }

    /** {@code playerTerm} is null when the query carried no name text (e.g. only a year). */
    public record ParsedQuery(String playerTerm, Integer year, String cardNumber) {
    }
}

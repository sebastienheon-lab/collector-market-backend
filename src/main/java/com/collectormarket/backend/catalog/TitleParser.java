package com.collectormarket.backend.catalog;

import java.time.Year;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

/**
 * Rule-based eBay title parser (§10 Phase 1). No fuzzy matching - year, brand/set, and card
 * number come from regexes; player name comes from an exact (normalized) substring check
 * against the seeded catalog's player names, per the "exact match against seed only" decision.
 */
@Component
public class TitleParser {

    private static final Pattern YEAR_PATTERN = Pattern.compile("\\b(19|20)\\d{2}\\b");
    private static final Pattern CARD_NUMBER_PATTERN = Pattern.compile("#([A-Za-z0-9-]+)");
    private static final int MIN_PLAUSIBLE_YEAR = 1980;

    private static final List<BrandSetRule> BRAND_SET_RULES = List.of(
            new BrandSetRule(Pattern.compile("bowman\\s+chrome", Pattern.CASE_INSENSITIVE), "Bowman", "Chrome"),
            new BrandSetRule(Pattern.compile("topps\\s+chrome", Pattern.CASE_INSENSITIVE), "Topps", "Chrome"),
            new BrandSetRule(Pattern.compile("panini\\s+prizm", Pattern.CASE_INSENSITIVE), "Panini", "Prizm"),
            new BrandSetRule(Pattern.compile("upper\\s+deck\\s+young\\s+guns", Pattern.CASE_INSENSITIVE),
                    "Upper Deck", "Young Guns"),
            new BrandSetRule(Pattern.compile("young\\s+guns", Pattern.CASE_INSENSITIVE), "Upper Deck", "Young Guns"),
            new BrandSetRule(Pattern.compile("prizm", Pattern.CASE_INSENSITIVE), "Panini", "Prizm"),
            new BrandSetRule(Pattern.compile("topps\\s+basketball", Pattern.CASE_INSENSITIVE), "Topps", "Basketball"));

    // Parallel/color/insert/relic indicators - a hit here means the listing is NOT the plain
    // base card the seed catalog carries, so it's routed to unmatched-listing for triage rather
    // than silently matched onto the base card's id. Widened beyond parallel colors after
    // checking real listing titles during M4 (e.g. "Rookie Auto PSA 10" would otherwise
    // false-match the base card whenever the title has no #cardNumber to disambiguate).
    private static final Set<String> VARIANT_KEYWORDS = Set.of(
            "silver", "gold", "green", "red", "orange", "purple", "black", "pink",
            "refractor", "xfractor", "shimmer", "sepia", "mojo", "pulsar", "camo", "wave",
            "kaboom", "hyper", "scope", "disco", "choice", "parallel",
            "auto", "autograph", "autographed", "relic", "patch", "memorabilia", "jumbo");

    // "blue" and "jersey" are excluded from the plain word-list above because they collide with
    // real team names as bare words (Columbus Blue Jackets; New Jersey Devils) - real listing
    // fixtures caught this. Require them to appear in an actually variant-indicating phrase instead.
    private static final Pattern BLUE_VARIANT_PATTERN =
            Pattern.compile("\\bblue\\s+(prizm|refractor|xfractor|wave|scope|pulsar|mojo|hyper)\\b",
                    Pattern.CASE_INSENSITIVE);
    private static final Pattern JERSEY_VARIANT_PATTERN =
            Pattern.compile("\\bjersey\\s+(patch|number|card|swatch)\\b", Pattern.CASE_INSENSITIVE);

    public ParsedTitle parse(String rawTitle, Set<String> knownPlayerNames) {
        Integer year = extractYear(rawTitle);
        String cardNumber = extractCardNumber(rawTitle);
        BrandSetRule brandSet = extractBrandSet(rawTitle);
        String playerName = matchPlayerName(rawTitle, knownPlayerNames);
        boolean variant = containsVariantKeyword(rawTitle);
        GradeInfo grade = GradeNormalizer.extract(rawTitle);

        return new ParsedTitle(
                year,
                playerName,
                brandSet != null ? brandSet.brand() : null,
                brandSet != null ? brandSet.setName() : null,
                cardNumber,
                grade.gradeSource(),
                grade.gradeValue(),
                variant);
    }

    private Integer extractYear(String title) {
        Matcher matcher = YEAR_PATTERN.matcher(title);
        int currentYearPlusOne = Year.now().getValue() + 1;
        while (matcher.find()) {
            int candidate = Integer.parseInt(matcher.group());
            if (candidate >= MIN_PLAUSIBLE_YEAR && candidate <= currentYearPlusOne) {
                return candidate;
            }
        }
        return null;
    }

    private String extractCardNumber(String title) {
        Matcher matcher = CARD_NUMBER_PATTERN.matcher(title);
        return matcher.find() ? matcher.group(1) : null;
    }

    private BrandSetRule extractBrandSet(String title) {
        for (BrandSetRule rule : BRAND_SET_RULES) {
            if (rule.pattern().matcher(title).find()) {
                return rule;
            }
        }
        return null;
    }

    private boolean containsVariantKeyword(String title) {
        String normalized = normalize(title);
        for (String keyword : VARIANT_KEYWORDS) {
            if (containsWord(normalized, keyword)) {
                return true;
            }
        }
        return BLUE_VARIANT_PATTERN.matcher(title).find() || JERSEY_VARIANT_PATTERN.matcher(title).find();
    }

    private String matchPlayerName(String title, Set<String> knownPlayerNames) {
        String normalizedTitle = normalize(title);
        String best = null;
        int bestLength = -1;
        for (String candidate : knownPlayerNames) {
            String normalizedCandidate = normalize(candidate);
            if (normalizedTitle.contains(normalizedCandidate) && normalizedCandidate.length() > bestLength) {
                best = candidate;
                bestLength = normalizedCandidate.length();
            }
        }
        return best;
    }

    private boolean containsWord(String normalizedHaystack, String word) {
        return (" " + normalizedHaystack + " ").contains(" " + word + " ");
    }

    private String normalize(String value) {
        return value.toLowerCase()
                .replaceAll("[.'’]", "")
                .replaceAll("[^a-z0-9]+", " ")
                .trim()
                .replaceAll("\\s+", " ");
    }

    private record BrandSetRule(Pattern pattern, String brand, String setName) {
    }
}

package com.collectormarket.backend.api;

/**
 * Canonical grade tokens accepted by the price-history / market / grade endpoints (§8.2-8.5).
 * <p>
 * Two sentinels plus one constant per grader×value:
 * <ul>
 *   <li>{@link #ALL} - no grade filter (aggregate across every grade).</li>
 *   <li>{@link #RAW} - ungraded, stored as {@code ("RAW","RAW")} to match
 *       {@link com.collectormarket.backend.catalog.GradeInfo#RAW}.</li>
 *   <li>{@code {PSA,BGS,SGC,CGC}{value}} where a half point is written with an underscore
 *       (e.g. {@code PSA9_5} == PSA 9.5) so the token is a legal Java identifier and renders
 *       verbatim as the OpenAPI enum value.</li>
 * </ul>
 * Because controllers bind this enum directly, an unrecognized token produces a Spring
 * {@code MethodArgumentTypeMismatchException}, which the exception handler maps to
 * {@code 400 INVALID_GRADE}.
 */
public enum Grade {

    ALL(null, null),
    RAW("RAW", "RAW"),

    PSA10("PSA", "10"), PSA9_5("PSA", "9.5"), PSA9("PSA", "9"), PSA8_5("PSA", "8.5"), PSA8("PSA", "8"),
    PSA7_5("PSA", "7.5"), PSA7("PSA", "7"), PSA6_5("PSA", "6.5"), PSA6("PSA", "6"), PSA5_5("PSA", "5.5"),
    PSA5("PSA", "5"), PSA4_5("PSA", "4.5"), PSA4("PSA", "4"), PSA3_5("PSA", "3.5"), PSA3("PSA", "3"),
    PSA2_5("PSA", "2.5"), PSA2("PSA", "2"), PSA1_5("PSA", "1.5"), PSA1("PSA", "1"),

    BGS10("BGS", "10"), BGS9_5("BGS", "9.5"), BGS9("BGS", "9"), BGS8_5("BGS", "8.5"), BGS8("BGS", "8"),
    BGS7_5("BGS", "7.5"), BGS7("BGS", "7"), BGS6_5("BGS", "6.5"), BGS6("BGS", "6"), BGS5_5("BGS", "5.5"),
    BGS5("BGS", "5"), BGS4_5("BGS", "4.5"), BGS4("BGS", "4"), BGS3_5("BGS", "3.5"), BGS3("BGS", "3"),
    BGS2_5("BGS", "2.5"), BGS2("BGS", "2"), BGS1_5("BGS", "1.5"), BGS1("BGS", "1"),

    SGC10("SGC", "10"), SGC9_5("SGC", "9.5"), SGC9("SGC", "9"), SGC8_5("SGC", "8.5"), SGC8("SGC", "8"),
    SGC7_5("SGC", "7.5"), SGC7("SGC", "7"), SGC6_5("SGC", "6.5"), SGC6("SGC", "6"), SGC5_5("SGC", "5.5"),
    SGC5("SGC", "5"), SGC4_5("SGC", "4.5"), SGC4("SGC", "4"), SGC3_5("SGC", "3.5"), SGC3("SGC", "3"),
    SGC2_5("SGC", "2.5"), SGC2("SGC", "2"), SGC1_5("SGC", "1.5"), SGC1("SGC", "1"),

    CGC10("CGC", "10"), CGC9_5("CGC", "9.5"), CGC9("CGC", "9"), CGC8_5("CGC", "8.5"), CGC8("CGC", "8"),
    CGC7_5("CGC", "7.5"), CGC7("CGC", "7"), CGC6_5("CGC", "6.5"), CGC6("CGC", "6"), CGC5_5("CGC", "5.5"),
    CGC5("CGC", "5"), CGC4_5("CGC", "4.5"), CGC4("CGC", "4"), CGC3_5("CGC", "3.5"), CGC3("CGC", "3"),
    CGC2_5("CGC", "2.5"), CGC2("CGC", "2"), CGC1_5("CGC", "1.5"), CGC1("CGC", "1");

    private final String gradeSource;
    private final String gradeValue;

    Grade(String gradeSource, String gradeValue) {
        this.gradeSource = gradeSource;
        this.gradeValue = gradeValue;
    }

    /** True for every token except {@link #ALL} - i.e. the query should constrain on grade. */
    public boolean filtersByGrade() {
        return this != ALL;
    }

    /** {@code null} for {@link #ALL}; otherwise the {@code price_snapshot.grade_source} value. */
    public String gradeSource() {
        return gradeSource;
    }

    /** {@code null} for {@link #ALL}; otherwise the {@code price_snapshot.grade_value} value. */
    public String gradeValue() {
        return gradeValue;
    }

    /**
     * Renders a stored {@code (grade_source, grade_value)} pair back into its canonical token
     * (the inverse of the per-constant mapping) for response bodies that surface a grade read
     * from the DB, e.g. the grade breakdown (§8.5). {@code ("RAW","RAW")} -&gt; {@code "RAW"};
     * {@code ("PSA","9.5")} -&gt; {@code "PSA9_5"}.
     */
    public static String tokenFor(String gradeSource, String gradeValue) {
        if (gradeSource == null || "RAW".equals(gradeSource)) {
            return RAW.name();
        }
        return gradeSource + gradeValue.replace('.', '_');
    }
}

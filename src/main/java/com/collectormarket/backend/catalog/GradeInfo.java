package com.collectormarket.backend.catalog;

/** gradeSource/gradeValue pair matching the price_snapshot/listing_observation convention (§7.3-7.4). */
public record GradeInfo(String gradeSource, String gradeValue) {

    public static final GradeInfo RAW = new GradeInfo("RAW", "RAW");
}

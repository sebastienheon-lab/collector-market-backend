package com.collectormarket.backend.api;

import java.util.List;

/**
 * Appends a {@code grade_source}/{@code grade_value} predicate for a {@link Grade} token, shared by
 * the market and price query stores (all three source tables use those column names). {@link
 * Grade#ALL} adds nothing (aggregate across grades). The optional table alias handles queries that
 * join and need a qualifier (e.g. {@code "lo"}).
 */
final class GradeFilter {

    private GradeFilter() {
    }

    static void append(StringBuilder sql, List<Object> args, Grade grade, String alias) {
        if (!grade.filtersByGrade()) {
            return;
        }
        String prefix = alias == null || alias.isEmpty() ? "" : alias + ".";
        sql.append("  AND ").append(prefix).append("grade_source = ?\n");
        sql.append("  AND ").append(prefix).append("grade_value = ?\n");
        args.add(grade.gradeSource());
        args.add(grade.gradeValue());
    }
}

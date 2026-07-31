package com.collectormarket.backend.domain;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Composite primary key for {@link MarketMetricDaily}: {@code (card_id, grade_source, grade_value,
 * metric_date)}. A plain {@code @IdClass} holder - it needs a no-arg constructor and value-based
 * {@code equals}/{@code hashCode} (unlike an entity, where those are discouraged).
 */
public class MarketMetricDailyId implements Serializable {

    private UUID cardId;
    private String gradeSource;
    private String gradeValue;
    private LocalDate metricDate;

    protected MarketMetricDailyId() {
    }

    public MarketMetricDailyId(UUID cardId, String gradeSource, String gradeValue, LocalDate metricDate) {
        this.cardId = cardId;
        this.gradeSource = gradeSource;
        this.gradeValue = gradeValue;
        this.metricDate = metricDate;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof MarketMetricDailyId that)) {
            return false;
        }
        return Objects.equals(cardId, that.cardId)
                && Objects.equals(gradeSource, that.gradeSource)
                && Objects.equals(gradeValue, that.gradeValue)
                && Objects.equals(metricDate, that.metricDate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(cardId, gradeSource, gradeValue, metricDate);
    }
}

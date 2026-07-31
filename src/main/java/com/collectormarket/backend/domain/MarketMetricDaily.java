package com.collectormarket.backend.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * JPA mapping of {@code market_metric_daily} (§7.11) - persistent daily aggregates, keyed by the
 * composite {@link MarketMetricDailyId}. Written by {@code DailyAggregator} (native upsert) and read
 * by the price-history/market queries (native aggregations). Schema owned by Flyway (V008);
 * {@code computed_at} is DB-defaulted and left unmapped.
 */
@Entity
@Table(name = "market_metric_daily")
@IdClass(MarketMetricDailyId.class)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MarketMetricDaily {

    @Id
    @Column(name = "card_id")
    private UUID cardId;

    @Id
    @Column(name = "grade_source")
    private String gradeSource;

    @Id
    @Column(name = "grade_value")
    private String gradeValue;

    @Id
    @Column(name = "metric_date")
    private LocalDate metricDate;

    @Column(name = "floor_price")
    private BigDecimal floorPrice;

    @Column(name = "median_ask")
    private BigDecimal medianAsk;

    @Column(name = "active_listings", nullable = false)
    private int activeListings;

    @Column(name = "new_listings", nullable = false)
    private int newListings;

    @Column(name = "delistings", nullable = false)
    private int delistings;
}

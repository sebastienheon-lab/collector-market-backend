package com.collectormarket.backend.domain;

import java.time.LocalDate;

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
 * JPA mapping of {@code api_call_counter} (M3) - the daily eBay call budget counter, keyed by the
 * composite {@link ApiCallCountId} of {@code (call_date, api_name)}. Named {@code ApiCallCount} to
 * avoid clashing with the {@code ebay.ApiCallCounter} service that guards the quota. The atomic
 * increment stays a native {@code INSERT ... ON CONFLICT ... RETURNING} on the repository - never a
 * read-modify-write - so concurrent reservations can't race. Schema owned by Flyway (V010).
 */
@Entity
@Table(name = "api_call_counter")
@IdClass(ApiCallCountId.class)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ApiCallCount {

    @Id
    @Column(name = "call_date")
    private LocalDate callDate;

    @Id
    @Column(name = "api_name")
    private String apiName;

    @Column(name = "call_count", nullable = false)
    private int callCount;
}

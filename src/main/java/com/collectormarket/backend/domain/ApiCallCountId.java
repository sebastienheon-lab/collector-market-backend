package com.collectormarket.backend.domain;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Composite primary key for {@link ApiCallCount}: {@code (call_date, api_name)}. Plain
 * {@code @IdClass} holder with a no-arg constructor and value-based equality.
 */
public class ApiCallCountId implements Serializable {

    private LocalDate callDate;
    private String apiName;

    protected ApiCallCountId() {
    }

    public ApiCallCountId(LocalDate callDate, String apiName) {
        this.callDate = callDate;
        this.apiName = apiName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ApiCallCountId that)) {
            return false;
        }
        return Objects.equals(callDate, that.callDate) && Objects.equals(apiName, that.apiName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(callDate, apiName);
    }
}

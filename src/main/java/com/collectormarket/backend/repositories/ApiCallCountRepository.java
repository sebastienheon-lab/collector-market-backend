package com.collectormarket.backend.repositories;

import com.collectormarket.backend.domain.*;

import java.time.LocalDate;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data repository for {@link ApiCallCount}. The daily budget increment stays an atomic
 * Postgres upsert: {@link #incrementAndGet} does {@code INSERT ... ON CONFLICT ... DO UPDATE ...
 * RETURNING call_count} in a single statement (never a read-modify-write), so concurrent
 * reservations can't race. Executed through Hibernate as a native query - no JdbcTemplate.
 */
public interface ApiCallCountRepository extends JpaRepository<ApiCallCount, ApiCallCountId> {

    @Query(value = """
            INSERT INTO api_call_counter (call_date, api_name, call_count)
            VALUES (:callDate, :apiName, 1)
            ON CONFLICT (call_date, api_name) DO UPDATE SET call_count = api_call_counter.call_count + 1
            RETURNING call_count
            """, nativeQuery = true)
    int incrementAndGet(@Param("callDate") LocalDate callDate, @Param("apiName") String apiName);

    @Modifying
    @Query(value = """
            UPDATE api_call_counter SET call_count = call_count - 1
            WHERE call_date = :callDate AND api_name = :apiName
            """, nativeQuery = true)
    void decrement(@Param("callDate") LocalDate callDate, @Param("apiName") String apiName);

    /** Total calls made on the given day across every api_name - backs the quota gauge/health. */
    @Query("SELECT COALESCE(SUM(a.callCount), 0) FROM ApiCallCount a WHERE a.callDate = :callDate")
    long totalCountOn(@Param("callDate") LocalDate callDate);
}

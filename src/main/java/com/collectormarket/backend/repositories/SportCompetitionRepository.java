package com.collectormarket.backend.repositories;

import com.collectormarket.backend.entities.*;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data repository for {@link SportCompetition}. */
public interface SportCompetitionRepository extends JpaRepository<SportCompetition, Short> {

    /**
     * §7.8-7.9 / OQ-15 forgetting-safety: for each active competition, its name plus the count of
     * events starting within {@code [today, horizon]}. Uses a Postgres {@code FILTER} aggregate
     * (no JPQL equivalent); returned as {@code [name, count]} rows.
     */
    @Query(value = """
            SELECT sc.name AS name,
                   COUNT(ce.id) FILTER (
                       WHERE ce.start_date >= :today AND ce.start_date <= :horizon
                   ) AS future_event_count
            FROM sport_competition sc
            LEFT JOIN competition_event ce ON ce.competition_id = sc.id
            WHERE sc.is_active = TRUE
            GROUP BY sc.id, sc.name
            """, nativeQuery = true)
    List<Object[]> countFutureEventsPerActiveCompetition(
            @Param("today") LocalDate today, @Param("horizon") LocalDate horizon);
}

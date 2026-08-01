package com.collectormarket.backend.repositories;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import com.collectormarket.backend.api.dto.EventBand;
import com.collectormarket.backend.entities.CompetitionEvent;

/**
 * Read repository (api layer) for competition event bands (§7.9). Native query - the {@code multi}
 * sport-code subquery has no clean JPQL form - projecting straight onto the {@link EventBand} DTO.
 */
public interface EventBandRepository extends Repository<CompetitionEvent, UUID> {

    @Query(value = """
            SELECT sc.name AS competition, ce.label AS label, ce.stage AS stage,
                   ce.start_date AS startDate, ce.end_date AS endDate
            FROM competition_event ce
            JOIN sport_competition sc ON sc.id = ce.competition_id
            JOIN card c ON c.id = :cardId
            WHERE sc.is_active = TRUE
              AND (sc.sport_id = c.sport_id
                   OR sc.sport_id = (SELECT id FROM sport WHERE code = 'multi'))
              AND ce.end_date   >= :fromDate
              AND ce.start_date <= :today
            ORDER BY ce.start_date ASC
            """, nativeQuery = true)
    List<EventBand> findEventBandsForCard(
            @Param("cardId") UUID cardId,
            @Param("fromDate") LocalDate fromDate,
            @Param("today") LocalDate today);
}

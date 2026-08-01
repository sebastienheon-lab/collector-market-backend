package com.collectormarket.backend.domain;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data repository for {@link CompetitionEvent}. */
public interface CompetitionEventRepository extends JpaRepository<CompetitionEvent, UUID> {

    /** Natural-key existence check for the idempotent reference-data loader. */
    boolean existsByCompetitionIdAndLabelAndStartDate(Short competitionId, String label, LocalDate startDate);
}

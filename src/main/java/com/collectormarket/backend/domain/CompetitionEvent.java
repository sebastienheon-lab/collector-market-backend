package com.collectormarket.backend.domain;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * JPA mapping of {@code competition_event} (§7.9) - concrete competition instances/stages with date
 * ranges, upserted by the reference-data loader (OQ-15). Schema owned by Flyway (V006).
 * {@code competition_id} is a raw {@link Short}; {@code created_at} is DB-defaulted and unmapped.
 */
@Entity
@Table(name = "competition_event")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CompetitionEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "competition_id", nullable = false)
    private Short competitionId;

    @Column(nullable = false)
    private String label;

    @Column(name = "stage")
    private String stage;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    /** Creates a new event instance (used by the reference-data loader). The id is generated on insert. */
    public CompetitionEvent(Short competitionId, String label, String stage,
            LocalDate startDate, LocalDate endDate) {
        this.competitionId = competitionId;
        this.label = label;
        this.stage = stage;
        this.startDate = startDate;
        this.endDate = endDate;
    }
}

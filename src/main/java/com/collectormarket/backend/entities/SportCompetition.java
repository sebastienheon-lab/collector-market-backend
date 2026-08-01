package com.collectormarket.backend.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * JPA mapping of {@code sport_competition} (§7.8) - named recurring competitions. Schema + seed rows
 * owned by Flyway (V005); {@code id} is an assigned SMALLINT, so no {@code @GeneratedValue}.
 * {@code sport_id} is mapped as a raw {@link Short}.
 */
@Entity
@Table(name = "sport_competition")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SportCompetition {

    @Id
    private Short id;

    @Column(name = "sport_id", nullable = false)
    private Short sportId;

    @Column(nullable = false)
    private String name;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    /** Assigned-id row for the reference-data upsert (save() merges by the given id). */
    public SportCompetition(Short id, Short sportId, String name, boolean active) {
        this.id = id;
        this.sportId = sportId;
        this.name = name;
        this.active = active;
    }
}

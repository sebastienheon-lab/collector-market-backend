package com.collectormarket.backend.domain;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for {@link Card} - the front door for card CRUD/simple reads in the JPA
 * pilot. {@code existsById} (inherited) backs the shared existence guard. {@link #findWithSportById}
 * eagerly fetches the {@code sport} association in one query so the detail DTO can read the sport
 * code without a lazy load after the session closes.
 */
public interface CardRepository extends JpaRepository<Card, UUID> {

    @EntityGraph(attributePaths = "sport")
    Optional<Card> findWithSportById(UUID id);
}

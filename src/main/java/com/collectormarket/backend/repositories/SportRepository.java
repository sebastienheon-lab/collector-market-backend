package com.collectormarket.backend.repositories;

import com.collectormarket.backend.domain.*;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for {@link Sport}. {@link #findByCode} resolves the human-readable sport
 * code (e.g. {@code "baseball"}) to its row - used by the seed loader and search's sport filter.
 */
public interface SportRepository extends JpaRepository<Sport, Short> {

    Optional<Sport> findByCode(String code);
}

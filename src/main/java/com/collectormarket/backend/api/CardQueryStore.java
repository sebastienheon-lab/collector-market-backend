package com.collectormarket.backend.api;

import java.util.Optional;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Sport-code resolution for search. Card detail + existence moved to {@code CardRepository} (JPA
 * pilot); this store now holds only the sport lookup used by {@link SearchService}. A natural next
 * step is a {@code SportRepository.findByCode} so this class can retire.
 */
@Component
public class CardQueryStore {

    private final JdbcTemplate jdbcTemplate;

    public CardQueryStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Resolves a human-readable {@code sport.code} to its internal id, empty if unknown. */
    public Optional<Short> findSportId(String sportCode) {
        try {
            Short id = jdbcTemplate.queryForObject(
                    "SELECT id FROM sport WHERE code = ?", Short.class, sportCode);
            return Optional.ofNullable(id);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }
}

package com.collectormarket.backend.api;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import com.collectormarket.backend.api.dto.EventBand;

/**
 * Competition event bands for a card's price chart (§7.9 selection rule): events whose competition
 * belongs to the card's sport <em>or</em> the {@code multi} sport, intersected with the requested
 * time window. The window mirrors the price series - {@code [today - days, today]} - so every band
 * falls on the chart's historical axis (see the note in {@code PriceHistoryService}).
 */
@Component
public class EventQueryStore {

    private static final RowMapper<EventBand> MAPPER = (rs, rowNum) -> new EventBand(
            rs.getString("competition"),
            rs.getString("label"),
            rs.getString("stage"),
            rs.getObject("start_date", LocalDate.class),
            rs.getObject("end_date", LocalDate.class));

    private final JdbcTemplate jdbcTemplate;

    public EventQueryStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<EventBand> eventsForCard(UUID cardId, int days) {
        return jdbcTemplate.query("""
                SELECT sc.name AS competition, ce.label, ce.stage, ce.start_date, ce.end_date
                FROM competition_event ce
                JOIN sport_competition sc ON sc.id = ce.competition_id
                JOIN card c ON c.id = ?
                WHERE sc.is_active = TRUE
                  AND (sc.sport_id = c.sport_id
                       OR sc.sport_id = (SELECT id FROM sport WHERE code = 'multi'))
                  AND ce.end_date   >= current_date - ?
                  AND ce.start_date <= current_date
                ORDER BY ce.start_date ASC
                """, MAPPER, cardId, days);
    }
}

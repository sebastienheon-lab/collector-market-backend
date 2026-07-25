package com.collectormarket.backend.referencedata;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

/**
 * Idempotent startup loader for {@code sport_competition} and {@code competition_event} (§7.8-7.9, OQ-15).
 * Kept separate from Flyway migrations — schema and reference data are different concerns, and this data
 * is meant to be edited via PR to seed YAML files under {@code src/main/resources/seeds/} without a migration.
 */
@Component
public class ReferenceDataLoader implements ApplicationRunner {

    private static final String SPORT_COMPETITIONS_SEED = "seeds/sport_competitions.yml";
    private static final String COMPETITION_EVENTS_SEED = "seeds/competition_events.yml";

    private final JdbcTemplate jdbcTemplate;

    public ReferenceDataLoader(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        loadSportCompetitions();
        loadCompetitionEvents();
    }

    private void loadSportCompetitions() throws IOException {
        for (Map<String, Object> row : loadYaml(SPORT_COMPETITIONS_SEED)) {
            jdbcTemplate.update("""
                INSERT INTO sport_competition (id, sport_id, name, is_active)
                VALUES (?, (SELECT id FROM sport WHERE code = ?), ?, ?)
                ON CONFLICT (id) DO UPDATE SET
                    sport_id  = EXCLUDED.sport_id,
                    name      = EXCLUDED.name,
                    is_active = EXCLUDED.is_active
                """,
                row.get("id"), row.get("sportCode"), row.get("name"), row.get("active"));
        }
    }

    private void loadCompetitionEvents() throws IOException {
        for (Map<String, Object> row : loadYaml(COMPETITION_EVENTS_SEED)) {
            Integer competitionId = (Integer) row.get("competitionId");
            String label = (String) row.get("label");
            Date startDate = Date.valueOf(LocalDate.parse((String) row.get("startDate")));

            Boolean alreadyLoaded = jdbcTemplate.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM competition_event
                    WHERE competition_id = ? AND label = ? AND start_date = ?
                )
                """, Boolean.class, competitionId, label, startDate);

            if (Boolean.TRUE.equals(alreadyLoaded)) {
                continue;
            }

            Date endDate = Date.valueOf(LocalDate.parse((String) row.get("endDate")));
            jdbcTemplate.update("""
                INSERT INTO competition_event (competition_id, label, stage, start_date, end_date)
                VALUES (?, ?, ?, ?, ?)
                """,
                competitionId, label, row.get("stage"), startDate, endDate);
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> loadYaml(String classpathLocation) throws IOException {
        try (InputStream in = new ClassPathResource(classpathLocation).getInputStream()) {
            List<Map<String, Object>> data = new Yaml().load(in);
            return data != null ? data : List.of();
        }
    }
}

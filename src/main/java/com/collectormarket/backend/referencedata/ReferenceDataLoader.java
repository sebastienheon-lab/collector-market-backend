package com.collectormarket.backend.referencedata;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import com.collectormarket.backend.domain.CompetitionEvent;
import com.collectormarket.backend.repositories.CompetitionEventRepository;
import com.collectormarket.backend.domain.Sport;
import com.collectormarket.backend.domain.SportCompetition;
import com.collectormarket.backend.repositories.SportCompetitionRepository;
import com.collectormarket.backend.repositories.SportRepository;

/**
 * Idempotent startup loader for {@code sport_competition} and {@code competition_event} (§7.8-7.9, OQ-15).
 * Kept separate from Flyway migrations — schema and reference data are different concerns, and this data
 * is meant to be edited via PR to seed YAML files under {@code src/main/resources/seeds/} without a migration.
 * <p>
 * Competitions have assigned ids, so {@code save} merges by id (upsert). Events have generated ids and
 * no natural-unique constraint, so they're inserted only when a (competition, label, start_date) row
 * doesn't already exist.
 */
@Component
public class ReferenceDataLoader implements ApplicationRunner {

    private static final String SPORT_COMPETITIONS_SEED = "seeds/sport_competitions.yml";
    private static final String COMPETITION_EVENTS_SEED = "seeds/competition_events.yml";

    private final SportRepository sportRepository;
    private final SportCompetitionRepository sportCompetitionRepository;
    private final CompetitionEventRepository competitionEventRepository;

    public ReferenceDataLoader(SportRepository sportRepository,
            SportCompetitionRepository sportCompetitionRepository,
            CompetitionEventRepository competitionEventRepository) {
        this.sportRepository = sportRepository;
        this.sportCompetitionRepository = sportCompetitionRepository;
        this.competitionEventRepository = competitionEventRepository;
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        loadSportCompetitions();
        loadCompetitionEvents();
    }

    private void loadSportCompetitions() throws IOException {
        for (Map<String, Object> row : loadYaml(SPORT_COMPETITIONS_SEED)) {
            String sportCode = (String) row.get("sportCode");
            Short sportId = sportRepository.findByCode(sportCode)
                    .map(Sport::getId)
                    .orElseThrow(() -> new IllegalStateException("Unknown sport code in seed: " + sportCode));

            sportCompetitionRepository.save(new SportCompetition(
                    ((Integer) row.get("id")).shortValue(),
                    sportId,
                    (String) row.get("name"),
                    Boolean.TRUE.equals(row.get("active"))));
        }
    }

    private void loadCompetitionEvents() throws IOException {
        for (Map<String, Object> row : loadYaml(COMPETITION_EVENTS_SEED)) {
            Short competitionId = ((Integer) row.get("competitionId")).shortValue();
            String label = (String) row.get("label");
            LocalDate startDate = LocalDate.parse((String) row.get("startDate"));

            if (competitionEventRepository
                    .existsByCompetitionIdAndLabelAndStartDate(competitionId, label, startDate)) {
                continue;
            }

            competitionEventRepository.save(new CompetitionEvent(
                    competitionId, label, (String) row.get("stage"),
                    startDate, LocalDate.parse((String) row.get("endDate"))));
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

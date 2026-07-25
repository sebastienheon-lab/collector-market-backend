package com.collectormarket.backend.catalog;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

/**
 * Idempotent startup loader for the hand-curated card catalog seed (§10 Phase 1, OQ-2).
 * Mirrors {@code ReferenceDataLoader}'s pattern: reads YAML under {@code src/main/resources/seeds/},
 * upserting by natural key rather than a DB-level upsert (no unique constraint on the card
 * table beyond its UUID PK - see M2 for why that's the schema as given).
 * <p>
 * Natural key is (player, year, brand, set) only - NOT cardNumber. cardNumber starts null for
 * most rows and gets filled in as real numbers are confirmed; if it were part of the key, a
 * later correction would insert a duplicate row instead of updating the existing one.
 */
@Component
public class CardSeedLoader implements ApplicationRunner {

    private static final String CARDS_SEED = "seeds/cards.yml";

    private final JdbcTemplate jdbcTemplate;

    public CardSeedLoader(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        for (Map<String, Object> row : loadYaml()) {
            upsert(row);
        }
    }

    private void upsert(Map<String, Object> row) {
        String playerName = (String) row.get("playerName");
        Integer year = (Integer) row.get("year");
        String brand = (String) row.get("brand");
        String setName = (String) row.get("setName");
        String cardNumber = (String) row.get("cardNumber");
        String sportCode = (String) row.get("sportCode");
        boolean isRookie = Boolean.TRUE.equals(row.get("isRookie"));

        int updated = jdbcTemplate.update("""
                UPDATE card
                SET card_number = ?, sport_id = (SELECT id FROM sport WHERE code = ?), is_rookie = ?
                WHERE player_name = ? AND year = ?
                  AND brand IS NOT DISTINCT FROM ? AND set_name IS NOT DISTINCT FROM ?
                """, cardNumber, sportCode, isRookie, playerName, year, brand, setName);

        if (updated > 0) {
            return;
        }

        jdbcTemplate.update("""
                INSERT INTO card (player_name, year, brand, set_name, card_number, sport_id, is_rookie)
                VALUES (?, ?, ?, ?, ?, (SELECT id FROM sport WHERE code = ?), ?)
                """, playerName, year, brand, setName, cardNumber, sportCode, isRookie);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> loadYaml() throws IOException {
        try (InputStream in = new ClassPathResource(CARDS_SEED).getInputStream()) {
            Map<String, Object> root = new Yaml().load(in);
            List<Map<String, Object>> cards = (List<Map<String, Object>>) root.get("cards");
            return cards != null ? cards : List.of();
        }
    }
}

package com.collectormarket.backend.catalog;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import com.collectormarket.backend.entities.Card;
import com.collectormarket.backend.repositories.CardRepository;
import com.collectormarket.backend.entities.Sport;
import com.collectormarket.backend.repositories.SportRepository;

/**
 * Idempotent startup loader for the hand-curated card catalog seed (§10 Phase 1, OQ-2). Mirrors
 * {@code ReferenceDataLoader}'s pattern: reads YAML under {@code src/main/resources/seeds/},
 * upserting by natural key (find-or-create via {@link CardRepository}) rather than a DB-level upsert
 * (no unique constraint on the card table beyond its UUID PK - see M2 for why that's the schema).
 * <p>
 * Natural key is (player, year, brand, set) only - NOT cardNumber. cardNumber starts null for most
 * rows and gets filled in as real numbers are confirmed; if it were part of the key, a later
 * correction would insert a duplicate row instead of updating the existing one.
 * <p>
 * Ordered before {@code CardTracker} (M5), which seed-tracks any card lacking a {@code card_tracking}
 * row on startup and needs this loader's rows to exist first.
 */
@Component
@Order(1)
public class CardSeedLoader implements ApplicationRunner {

    private static final String CARDS_SEED = "seeds/cards.yml";

    private final CardRepository cardRepository;
    private final SportRepository sportRepository;

    public CardSeedLoader(CardRepository cardRepository, SportRepository sportRepository) {
        this.cardRepository = cardRepository;
        this.sportRepository = sportRepository;
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        for (Map<String, Object> row : loadYaml()) {
            upsert(row);
        }
    }

    private void upsert(Map<String, Object> row) {
        String playerName = (String) row.get("playerName");
        Short year = ((Integer) row.get("year")).shortValue();
        String brand = (String) row.get("brand");
        String setName = (String) row.get("setName");
        String cardNumber = (String) row.get("cardNumber");
        String sportCode = (String) row.get("sportCode");
        boolean isRookie = Boolean.TRUE.equals(row.get("isRookie"));

        Sport sport = sportRepository.findByCode(sportCode)
                .orElseThrow(() -> new IllegalStateException("Unknown sport code in seed: " + sportCode));

        List<Card> existing = cardRepository.findByNaturalKey(playerName, year, brand, setName);
        if (existing.isEmpty()) {
            cardRepository.save(new Card(playerName, year, brand, setName, cardNumber, sport, isRookie));
            return;
        }
        for (Card card : existing) {
            card.setCardNumber(cardNumber);
            card.setSport(sport);
            card.setRookie(isRookie);
        }
        cardRepository.saveAll(existing);
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

package com.collectormarket.backend.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.collectormarket.backend.api.dto.EventBand;
import com.collectormarket.backend.domain.CompetitionEvent;
import com.collectormarket.backend.repositories.CompetitionEventRepository;

/**
 * Verifies event bands are read and projected onto the {@link EventBand} record from a native
 * query (proves record-from-native mapping works with a real row, which an empty result wouldn't).
 */
class EventQueryStoreIntegrationTest extends ApiIntegrationTestBase {

    @Autowired
    private EventQueryStore eventQueryStore;

    @Autowired
    private CompetitionEventRepository competitionEventRepository;

    @Test
    void eventsForCard_projectsBaseballCompetitionEventOntoEventBand() {
        UUID cardId = insertCard("Event Player " + UUID.randomUUID(), 2024, "Topps", "Chrome", "1");
        String label = "Test Postseason " + UUID.randomUUID();
        // competition 1 = 'MLB Postseason' (baseball), seeded in V005; add an in-window instance.
        competitionEventRepository.save(new CompetitionEvent(
                (short) 1, label, "Finals", LocalDate.now().minusDays(5), LocalDate.now().plusDays(5)));

        List<EventBand> bands = eventQueryStore.eventsForCard(cardId, 90);

        assertThat(bands).anySatisfy(band -> {
            assertThat(band.competition()).isEqualTo("MLB Postseason");
            assertThat(band.label()).isEqualTo(label);
            assertThat(band.stage()).isEqualTo("Finals");
            assertThat(band.startDate()).isEqualTo(LocalDate.now().minusDays(5));
            assertThat(band.endDate()).isEqualTo(LocalDate.now().plusDays(5));
        });
    }
}

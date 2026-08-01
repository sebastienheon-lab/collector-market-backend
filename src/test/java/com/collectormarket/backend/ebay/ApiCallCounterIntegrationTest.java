package com.collectormarket.backend.ebay;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Verifies the atomic daily-budget counter against a real Postgres - specifically that the native
 * {@code INSERT ... ON CONFLICT ... RETURNING} upsert (executed through Hibernate, no JdbcTemplate)
 * actually returns the post-increment count. Every other test mocks {@code ApiCallCounter}, so this
 * is the only coverage of its real DB path.
 */
@Testcontainers
@SpringBootTest
class ApiCallCounterIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private ApiCallCounter apiCallCounter;

    @Test
    void reserveCallSlot_incrementsAndReturnsSlotUntilLimitThenBlocks() {
        String api = "test.api." + UUID.randomUUID(); // isolate today's counter from other rows
        int limit = 3;

        assertThat(apiCallCounter.reserveCallSlot(api, limit)).isTrue();  // 1
        assertThat(apiCallCounter.reserveCallSlot(api, limit)).isTrue();  // 2
        assertThat(apiCallCounter.reserveCallSlot(api, limit)).isTrue();  // 3
        assertThat(apiCallCounter.currentCount(api)).isEqualTo(3);

        // Fourth would exceed the limit: the atomic increment is rolled back and the slot denied.
        assertThat(apiCallCounter.reserveCallSlot(api, limit)).isFalse();
        assertThat(apiCallCounter.currentCount(api)).isEqualTo(3);
    }

    @Test
    void totalCountToday_sumsAcrossApis() {
        int before = apiCallCounter.totalCountToday();
        String api = "test.total." + UUID.randomUUID();

        apiCallCounter.reserveCallSlot(api, 100);
        apiCallCounter.reserveCallSlot(api, 100);

        assertThat(apiCallCounter.totalCountToday()).isEqualTo(before + 2);
    }
}

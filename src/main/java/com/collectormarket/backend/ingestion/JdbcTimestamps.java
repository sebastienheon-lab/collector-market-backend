package com.collectormarket.backend.ingestion;

import java.sql.Timestamp;
import java.time.Instant;

/** pgjdbc's setObject() can't infer a SQL type for a bare java.time.Instant bind parameter. */
final class JdbcTimestamps {

    private JdbcTimestamps() {
    }

    static Timestamp toTimestamp(Instant instant) {
        return instant != null ? Timestamp.from(instant) : null;
    }
}

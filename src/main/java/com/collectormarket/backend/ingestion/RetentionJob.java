package com.collectormarket.backend.ingestion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * §5.4 / OQ-9 retention enforcement: deletes expired {@code listing_observation} rows and
 * nullifies expired {@code price_snapshot} linkback fields. Windows come from
 * {@link RetentionProperties} (application.yml, not app_setting - see its javadoc for why).
 */
@Component
public class RetentionJob {

    private static final Logger log = LoggerFactory.getLogger(RetentionJob.class);

    private final JdbcTemplate jdbcTemplate;
    private final RetentionProperties retentionProperties;

    public RetentionJob(JdbcTemplate jdbcTemplate, RetentionProperties retentionProperties) {
        this.jdbcTemplate = jdbcTemplate;
        this.retentionProperties = retentionProperties;
    }

    public void run() {
        int deletedObservations = jdbcTemplate.update(
                "DELETE FROM listing_observation WHERE observed_at < now() - make_interval(days => ?)",
                retentionProperties.listingObservationDays());

        int nullifiedLinkbacks = jdbcTemplate.update("""
                UPDATE price_snapshot
                SET external_url = NULL, raw_title = NULL, external_id = NULL
                WHERE sold_at < now() - make_interval(days => ?)
                  AND (external_url IS NOT NULL OR raw_title IS NOT NULL OR external_id IS NOT NULL)
                """, retentionProperties.priceSnapshotLinkbackDays());

        log.info("retention_job deletedObservations={} nullifiedLinkbacks={}", deletedObservations, nullifiedLinkbacks);
    }
}

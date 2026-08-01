package com.collectormarket.backend.ingestion;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.collectormarket.backend.repositories.ListingObservationRepository;
import com.collectormarket.backend.repositories.PriceSnapshotRepository;

/**
 * §5.4 / OQ-9 retention enforcement: deletes expired {@code listing_observation} rows and
 * nullifies expired {@code price_snapshot} linkback fields. Windows come from
 * {@link RetentionProperties} (application.yml, not app_setting - see its javadoc for why); the
 * cutoff instants are computed here and passed to the repositories.
 */
@Component
public class RetentionJob {

    private static final Logger log = LoggerFactory.getLogger(RetentionJob.class);

    private final ListingObservationRepository listingObservationRepository;
    private final PriceSnapshotRepository priceSnapshotRepository;
    private final RetentionProperties retentionProperties;

    public RetentionJob(ListingObservationRepository listingObservationRepository,
            PriceSnapshotRepository priceSnapshotRepository, RetentionProperties retentionProperties) {
        this.listingObservationRepository = listingObservationRepository;
        this.priceSnapshotRepository = priceSnapshotRepository;
        this.retentionProperties = retentionProperties;
    }

    public void run() {
        Instant now = Instant.now();
        int deletedObservations = listingObservationRepository.deleteObservedBefore(
                now.minus(retentionProperties.listingObservationDays(), ChronoUnit.DAYS));

        int nullifiedLinkbacks = priceSnapshotRepository.nullifyLinkbacksSoldBefore(
                now.minus(retentionProperties.priceSnapshotLinkbackDays(), ChronoUnit.DAYS));

        log.info("retention_job deletedObservations={} nullifiedLinkbacks={}", deletedObservations, nullifiedLinkbacks);
    }
}

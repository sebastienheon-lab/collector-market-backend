package com.collectormarket.backend.ingestion;

import java.time.LocalDate;
import java.time.ZoneOffset;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.collectormarket.backend.observability.JobMetrics;

/**
 * Aggregation and retention are one job, run in-process in that order - never two separately
 * scheduled jobs relying on cron timing to land in the right sequence. Aggregation must see a
 * day's listing_observation rows before retention deletes them.
 */
@Component
public class NightlyPipelineJob {

    private static final Logger log = LoggerFactory.getLogger(NightlyPipelineJob.class);

    private final DailyAggregator dailyAggregator;
    private final RetentionJob retentionJob;
    private final JobMetrics jobMetrics;

    public NightlyPipelineJob(DailyAggregator dailyAggregator, RetentionJob retentionJob, JobMetrics jobMetrics) {
        this.dailyAggregator = dailyAggregator;
        this.retentionJob = retentionJob;
        this.jobMetrics = jobMetrics;
    }

    @Scheduled(cron = "0 0 2 * * *", zone = "UTC")
    public void run() {
        jobMetrics.run("nightly_pipeline", () -> {
            LocalDate yesterday = LocalDate.now(ZoneOffset.UTC).minusDays(1);
            log.info("nightly_pipeline_starting metricDate={}", yesterday);

            dailyAggregator.aggregate(yesterday);
            retentionJob.run();

            log.info("nightly_pipeline_finished metricDate={}", yesterday);
        });
    }
}

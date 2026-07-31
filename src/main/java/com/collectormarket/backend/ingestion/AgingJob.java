package com.collectormarket.backend.ingestion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.collectormarket.backend.observability.JobMetrics;

/**
 * §7.10 nightly aging: SEARCHED -&gt; DECAYED after {@code tracking.decay_after_days} without
 * engagement, then poll_cadence -&gt; PAUSED (tier stays DECAYED - PAUSED is a poll_cadence
 * value, not a tier) after {@code tracking.pause_after_days}. Both thresholds are measured from
 * last_engagement_at directly, not "time in current tier" - a card idle long enough to clear
 * both thresholds cascades through both transitions in the same run. SEED and WATCHLISTED cards
 * are never touched.
 */
@Component
public class AgingJob {

    private static final Logger log = LoggerFactory.getLogger(AgingJob.class);
    private static final String DECAY_SETTING = "tracking.decay_after_days";
    private static final String PAUSE_SETTING = "tracking.pause_after_days";
    private static final int DEFAULT_DECAY_DAYS = 14;
    private static final int DEFAULT_PAUSE_DAYS = 60;

    private final JdbcTemplate jdbcTemplate;
    private final AppSettingService appSettingService;
    private final JobMetrics jobMetrics;

    public AgingJob(JdbcTemplate jdbcTemplate, AppSettingService appSettingService, JobMetrics jobMetrics) {
        this.jdbcTemplate = jdbcTemplate;
        this.appSettingService = appSettingService;
        this.jobMetrics = jobMetrics;
    }

    @Scheduled(cron = "0 30 3 * * *", zone = "UTC")
    public void run() {
        jobMetrics.run("aging", () -> {
            int decayAfterDays = appSettingService.getInt(DECAY_SETTING, DEFAULT_DECAY_DAYS);
            int pauseAfterDays = appSettingService.getInt(PAUSE_SETTING, DEFAULT_PAUSE_DAYS);

            int decayed = jdbcTemplate.update("""
                    UPDATE card_tracking
                    SET tier = 'DECAYED', poll_cadence = 'WEEKLY'
                    WHERE tier = 'SEARCHED'
                      AND last_engagement_at < now() - make_interval(days => ?)
                    """, decayAfterDays);

            int paused = jdbcTemplate.update("""
                    UPDATE card_tracking
                    SET poll_cadence = 'PAUSED'
                    WHERE tier = 'DECAYED'
                      AND poll_cadence <> 'PAUSED'
                      AND last_engagement_at < now() - make_interval(days => ?)
                    """, pauseAfterDays);

            log.info("aging_job decayed={} paused={}", decayed, paused);
        });
    }
}

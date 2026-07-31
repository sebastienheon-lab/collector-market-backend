package com.collectormarket.backend.ingestion;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.collectormarket.backend.observability.JobMetrics;

/**
 * §7.8-7.9 / OQ-15 weekly forgetting-safety check: warns when an active competition has fewer
 * than {@code events.min_future_events} events within {@code events.check_horizon_months}
 * months. Logs at WARN only - no external notifications in MVP (upgrade to an alert once
 * observability is in place, per OQ-15).
 */
@Component
public class EventsHealthCheckJob {

    private static final Logger log = LoggerFactory.getLogger(EventsHealthCheckJob.class);
    private static final String MIN_FUTURE_EVENTS_SETTING = "events.min_future_events";
    private static final String CHECK_HORIZON_MONTHS_SETTING = "events.check_horizon_months";
    private static final int DEFAULT_MIN_FUTURE_EVENTS = 2;
    private static final int DEFAULT_CHECK_HORIZON_MONTHS = 6;

    private final JdbcTemplate jdbcTemplate;
    private final AppSettingService appSettingService;
    private final JobMetrics jobMetrics;

    public EventsHealthCheckJob(JdbcTemplate jdbcTemplate, AppSettingService appSettingService, JobMetrics jobMetrics) {
        this.jdbcTemplate = jdbcTemplate;
        this.appSettingService = appSettingService;
        this.jobMetrics = jobMetrics;
    }

    @Scheduled(cron = "0 0 4 * * MON", zone = "UTC")
    public void run() {
        jobMetrics.run("events_health_check", () -> {
            int minFutureEvents = appSettingService.getInt(MIN_FUTURE_EVENTS_SETTING, DEFAULT_MIN_FUTURE_EVENTS);
            int checkHorizonMonths = appSettingService.getInt(CHECK_HORIZON_MONTHS_SETTING, DEFAULT_CHECK_HORIZON_MONTHS);

            List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                    SELECT sc.name,
                           COUNT(ce.id) FILTER (
                               WHERE ce.start_date >= CURRENT_DATE
                                 AND ce.start_date <= CURRENT_DATE + make_interval(months => ?)
                           ) AS future_event_count
                    FROM sport_competition sc
                    LEFT JOIN competition_event ce ON ce.competition_id = sc.id
                    WHERE sc.is_active = TRUE
                    GROUP BY sc.id, sc.name
                    """, checkHorizonMonths);

            for (Map<String, Object> row : rows) {
                long futureEventCount = ((Number) row.get("future_event_count")).longValue();
                if (futureEventCount < minFutureEvents) {
                    log.warn("events_health_check_warning competition={} futureEventCount={} minRequired={} horizonMonths={}",
                            row.get("name"), futureEventCount, minFutureEvents, checkHorizonMonths);
                }
            }
        });
    }
}

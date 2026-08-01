package com.collectormarket.backend.ingestion;

import java.time.LocalDate;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.collectormarket.backend.domain.SportCompetitionRepository;
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

    private final SportCompetitionRepository sportCompetitionRepository;
    private final AppSettingService appSettingService;
    private final JobMetrics jobMetrics;

    public EventsHealthCheckJob(SportCompetitionRepository sportCompetitionRepository,
            AppSettingService appSettingService, JobMetrics jobMetrics) {
        this.sportCompetitionRepository = sportCompetitionRepository;
        this.appSettingService = appSettingService;
        this.jobMetrics = jobMetrics;
    }

    @Scheduled(cron = "0 0 4 * * MON", zone = "UTC")
    public void run() {
        jobMetrics.run("events_health_check", () -> {
            int minFutureEvents = appSettingService.getInt(MIN_FUTURE_EVENTS_SETTING, DEFAULT_MIN_FUTURE_EVENTS);
            int checkHorizonMonths = appSettingService.getInt(CHECK_HORIZON_MONTHS_SETTING, DEFAULT_CHECK_HORIZON_MONTHS);

            LocalDate today = LocalDate.now();
            LocalDate horizon = today.plusMonths(checkHorizonMonths);
            List<Object[]> rows =
                    sportCompetitionRepository.countFutureEventsPerActiveCompetition(today, horizon);

            for (Object[] row : rows) {
                String competition = (String) row[0];
                long futureEventCount = ((Number) row[1]).longValue();
                if (futureEventCount < minFutureEvents) {
                    log.warn("events_health_check_warning competition={} futureEventCount={} minRequired={} horizonMonths={}",
                            competition, futureEventCount, minFutureEvents, checkHorizonMonths);
                }
            }
        });
    }
}

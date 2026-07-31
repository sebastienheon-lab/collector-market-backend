package com.collectormarket.backend.observability;

import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

/**
 * Wraps a scheduled job body so every run records {@code job.duration} (a timer) and, on an
 * unhandled exception, {@code job.failures} (a counter) - both tagged with the job name. Jobs call
 * {@link #run(String, Runnable)} from their {@code @Scheduled} method so the metric brackets the
 * whole run regardless of how it exits.
 */
@Component
public class JobMetrics {

    private final MeterRegistry meterRegistry;

    public JobMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void run(String jobName, Runnable body) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            body.run();
        } catch (RuntimeException e) {
            meterRegistry.counter("job.failures", "job", jobName).increment();
            throw e;
        } finally {
            sample.stop(meterRegistry.timer("job.duration", "job", jobName));
        }
    }
}

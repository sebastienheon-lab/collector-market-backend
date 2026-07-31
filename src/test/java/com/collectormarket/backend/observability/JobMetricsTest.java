package com.collectormarket.backend.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class JobMetricsTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final JobMetrics jobMetrics = new JobMetrics(registry);

    @Test
    void recordsDurationAndNoFailureOnSuccess() {
        jobMetrics.run("nightly_pipeline", () -> { /* work */ });

        assertThat(registry.timer("job.duration", "job", "nightly_pipeline").count()).isEqualTo(1);
        assertThat(registry.find("job.failures").tag("job", "nightly_pipeline").counter()).isNull();
    }

    @Test
    void countsFailureAndRethrowsOnException() {
        RuntimeException boom = new IllegalStateException("boom");

        assertThatThrownBy(() -> jobMetrics.run("aging", () -> { throw boom; }))
                .isSameAs(boom);

        // Duration still recorded (finally), and the failure counter incremented.
        assertThat(registry.timer("job.duration", "job", "aging").count()).isEqualTo(1);
        assertThat(registry.counter("job.failures", "job", "aging").count()).isEqualTo(1.0);
    }
}

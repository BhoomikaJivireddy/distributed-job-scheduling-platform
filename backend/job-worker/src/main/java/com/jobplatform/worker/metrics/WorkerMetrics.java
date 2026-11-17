package com.jobplatform.worker.metrics;

import com.jobplatform.common.domain.JobType;
import com.jobplatform.worker.execution.WorkerRegistry;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class WorkerMetrics {

    private final MeterRegistry registry;

    public WorkerMetrics(MeterRegistry registry, WorkerRegistry workerRegistry) {
        this.registry = registry;
        Gauge.builder("jobs.running", workerRegistry, WorkerRegistry::currentExecutions)
                .description("Jobs currently executing on this worker")
                .register(registry);
    }

    public void jobCompleted(JobType jobType) {
        registry.counter("jobs.completed", "type", jobType.name()).increment();
    }

    public void jobFailed(JobType jobType, String outcome) {
        registry.counter("jobs.failed", "type", jobType.name(), "outcome", outcome).increment();
    }

    public void retryScheduled(JobType jobType) {
        registry.counter("jobs.retries.scheduled", "type", jobType.name()).increment();
    }

    public void duplicateDiscarded() {
        registry.counter("jobs.messages.discarded").increment();
    }

    public void recordDuration(JobType jobType, Duration duration) {
        Timer.builder("job.execution.duration")
                .description("Handler execution time")
                .tag("type", jobType.name())
                .register(registry)
                .record(duration);
    }
}

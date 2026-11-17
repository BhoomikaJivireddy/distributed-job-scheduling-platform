package com.jobplatform.manager.metrics;

import com.jobplatform.common.domain.JobType;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class ManagerMetrics {

    private final MeterRegistry registry;
    private final Counter dispatched;
    private final Counter dispatchFailures;
    private final Counter leasesRecovered;

    public ManagerMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.dispatched = Counter.builder("jobs.dispatched").description("Jobs published to RabbitMQ").register(registry);
        this.dispatchFailures = Counter.builder("jobs.dispatch.failures").description("Failed publish attempts").register(registry);
        this.leasesRecovered = Counter.builder("jobs.leases.recovered").description("Jobs recovered after lease expiry").register(registry);
    }

    public void jobSubmitted(JobType jobType) {
        registry.counter("jobs.submitted", "type", jobType.name()).increment();
    }

    public void jobDispatched() {
        dispatched.increment();
    }

    public void dispatchFailed() {
        dispatchFailures.increment();
    }

    public void leaseRecovered() {
        leasesRecovered.increment();
    }
}

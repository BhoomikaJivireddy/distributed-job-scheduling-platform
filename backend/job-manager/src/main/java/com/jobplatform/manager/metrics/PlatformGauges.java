package com.jobplatform.manager.metrics;

import com.jobplatform.common.domain.JobStatus;
import com.jobplatform.common.domain.WorkerStatus;
import com.jobplatform.common.repository.JobRepository;
import com.jobplatform.common.repository.WorkerRepository;
import com.jobplatform.manager.config.ManagerProperties;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class PlatformGauges {

    private final JobRepository jobRepository;
    private final WorkerRepository workerRepository;
    private final ManagerProperties properties;
    private final Clock clock;
    private final Map<JobStatus, AtomicLong> jobsByStatus = new EnumMap<>(JobStatus.class);
    private final AtomicLong activeWorkers = new AtomicLong();

    public PlatformGauges(MeterRegistry registry, JobRepository jobRepository, WorkerRepository workerRepository,
                          ManagerProperties properties, Clock clock) {
        this.jobRepository = jobRepository;
        this.workerRepository = workerRepository;
        this.properties = properties;
        this.clock = clock;
        for (JobStatus status : JobStatus.values()) {
            AtomicLong holder = new AtomicLong();
            jobsByStatus.put(status, holder);
            Gauge.builder("jobs.by.status", holder, AtomicLong::get)
                    .description("Jobs currently in each status")
                    .tag("status", status.name())
                    .register(registry);
        }
        Gauge.builder("workers.active", activeWorkers, AtomicLong::get)
                .description("Workers with a recent heartbeat")
                .register(registry);
    }

    @Scheduled(fixedDelayString = "${platform.manager.gauge-refresh-interval-ms}")
    public void refresh() {
        jobsByStatus.forEach((status, holder) -> holder.set(jobRepository.countByStatus(status)));
        LocalDateTime threshold = LocalDateTime.now(clock).minusSeconds(properties.workerTimeoutSeconds());
        activeWorkers.set(workerRepository.countByStatusAndLastHeartbeatAtAfter(WorkerStatus.ACTIVE, threshold));
    }
}

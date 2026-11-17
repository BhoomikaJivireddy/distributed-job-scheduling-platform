package com.jobplatform.worker.execution;

import com.jobplatform.common.domain.WorkerStatus;
import com.jobplatform.common.repository.WorkerRepository;
import com.jobplatform.worker.config.WorkerProperties;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class WorkerRegistry {

    private static final Logger log = LoggerFactory.getLogger(WorkerRegistry.class);

    private final WorkerRepository workerRepository;
    private final WorkerProperties properties;
    private final Clock clock;
    private final AtomicInteger currentExecutions = new AtomicInteger();

    public WorkerRegistry(WorkerRepository workerRepository, WorkerProperties properties, Clock clock) {
        this.workerRepository = workerRepository;
        this.properties = properties;
        this.clock = clock;
    }

    @PostConstruct
    public void register() {
        workerRepository.register(properties.id(), resolveHostname(), properties.concurrency(), LocalDateTime.now(clock));
        log.info("Worker {} registered with concurrency {}", properties.id(), properties.concurrency());
    }

    @Scheduled(fixedDelayString = "${platform.worker.heartbeat-interval-ms}")
    public void sendHeartbeat() {
        int updated = workerRepository.heartbeat(properties.id(), WorkerStatus.ACTIVE, currentExecutions.get(),
                LocalDateTime.now(clock));
        if (updated == 0) {
            register();
        }
    }

    @PreDestroy
    public void deregister() {
        workerRepository.markInactive(properties.id(), WorkerStatus.INACTIVE, LocalDateTime.now(clock));
        log.info("Worker {} marked inactive on shutdown", properties.id());
    }

    public void executionStarted() {
        currentExecutions.incrementAndGet();
    }

    public void executionFinished() {
        currentExecutions.decrementAndGet();
    }

    public int currentExecutions() {
        return currentExecutions.get();
    }

    public void recordCompleted() {
        workerRepository.incrementCompleted(properties.id(), LocalDateTime.now(clock));
    }

    public void recordFailed() {
        workerRepository.incrementFailed(properties.id(), LocalDateTime.now(clock));
    }

    private static String resolveHostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException exception) {
            return "unknown";
        }
    }
}

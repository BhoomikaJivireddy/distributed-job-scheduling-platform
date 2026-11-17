package com.jobplatform.manager.service;

import com.jobplatform.common.domain.WorkerStatus;
import com.jobplatform.common.repository.WorkerRepository;
import com.jobplatform.manager.api.dto.WorkerResponse;
import com.jobplatform.manager.config.ManagerProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class WorkerService {

    private static final Logger log = LoggerFactory.getLogger(WorkerService.class);

    private final WorkerRepository workerRepository;
    private final ManagerProperties properties;
    private final Clock clock;

    public WorkerService(WorkerRepository workerRepository, ManagerProperties properties, Clock clock) {
        this.workerRepository = workerRepository;
        this.properties = properties;
        this.clock = clock;
    }

    public List<WorkerResponse> list() {
        LocalDateTime threshold = heartbeatThreshold();
        return workerRepository.findAllByOrderByIdAsc().stream()
                .map(worker -> WorkerResponse.from(worker, threshold))
                .toList();
    }

    public int markStaleWorkersInactive() {
        LocalDateTime now = LocalDateTime.now(clock);
        int updated = workerRepository.markStaleInactive(WorkerStatus.INACTIVE, heartbeatThreshold(), now);
        if (updated > 0) {
            log.warn("{} worker(s) marked inactive after missing heartbeats", updated);
        }
        return updated;
    }

    private LocalDateTime heartbeatThreshold() {
        return LocalDateTime.now(clock).minusSeconds(properties.workerTimeoutSeconds());
    }
}

package com.jobplatform.manager.service;

import com.jobplatform.common.domain.ExecutionStatus;
import com.jobplatform.common.domain.JobStatus;
import com.jobplatform.common.domain.JobType;
import com.jobplatform.common.domain.WorkerStatus;
import com.jobplatform.common.repository.JobExecutionRepository;
import com.jobplatform.common.repository.JobRepository;
import com.jobplatform.common.repository.WorkerRepository;
import com.jobplatform.manager.api.dto.StatsResponse;
import com.jobplatform.manager.config.ManagerProperties;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class StatsService {

    private final JobRepository jobRepository;
    private final JobExecutionRepository executionRepository;
    private final WorkerRepository workerRepository;
    private final ManagerProperties properties;
    private final Clock clock;

    public StatsService(JobRepository jobRepository, JobExecutionRepository executionRepository,
                        WorkerRepository workerRepository, ManagerProperties properties, Clock clock) {
        this.jobRepository = jobRepository;
        this.executionRepository = executionRepository;
        this.workerRepository = workerRepository;
        this.properties = properties;
        this.clock = clock;
    }

    public StatsResponse currentStats() {
        LocalDateTime now = LocalDateTime.now(clock);

        Map<String, Long> byStatus = zeroFilled(JobStatus.values());
        fill(byStatus, jobRepository.countGroupedByStatus());
        Map<String, Long> byType = zeroFilled(JobType.values());
        fill(byType, jobRepository.countGroupedByType());
        Map<String, Long> executionsByStatus = zeroFilled(ExecutionStatus.values());
        fill(executionsByStatus, executionRepository.countGroupedByStatus());

        long total = byStatus.values().stream().mapToLong(Long::longValue).sum();
        long pending = byStatus.get(JobStatus.PENDING.name()) + byStatus.get(JobStatus.SCHEDULED.name())
                + byStatus.get(JobStatus.QUEUED.name()) + byStatus.get(JobStatus.RETRYING.name());
        long failed = byStatus.get(JobStatus.FAILED.name()) + byStatus.get(JobStatus.DEAD.name());

        LocalDateTime heartbeatThreshold = now.minusSeconds(properties.workerTimeoutSeconds());
        long activeWorkers = workerRepository.countByStatusAndLastHeartbeatAtAfter(WorkerStatus.ACTIVE, heartbeatThreshold);
        long knownWorkers = workerRepository.count();

        Double averageDuration = executionRepository.averageDurationMs(ExecutionStatus.COMPLETED);
        Long retries = jobRepository.totalRetries();

        return new StatsResponse(
                total,
                pending,
                byStatus.get(JobStatus.RUNNING.name()),
                byStatus.get(JobStatus.COMPLETED.name()),
                failed,
                activeWorkers,
                knownWorkers - activeWorkers,
                retries == null ? 0 : retries,
                executionRepository.countByStartedAtAfter(now.minusHours(1)),
                averageDuration == null ? 0 : Math.round(averageDuration),
                byStatus,
                byType,
                executionsByStatus);
    }

    private static Map<String, Long> zeroFilled(Enum<?>[] values) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (Enum<?> value : values) {
            counts.put(value.name(), 0L);
        }
        return counts;
    }

    private static void fill(Map<String, Long> counts, List<Object[]> rows) {
        for (Object[] row : rows) {
            counts.put(((Enum<?>) row[0]).name(), ((Number) row[1]).longValue());
        }
    }
}

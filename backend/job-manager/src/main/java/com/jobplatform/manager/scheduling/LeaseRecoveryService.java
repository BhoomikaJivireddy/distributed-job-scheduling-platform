package com.jobplatform.manager.scheduling;

import com.jobplatform.common.domain.ExecutionStatus;
import com.jobplatform.common.domain.Job;
import com.jobplatform.common.repository.JobRepository;
import com.jobplatform.common.service.AttemptRef;
import com.jobplatform.common.service.JobOutcome;
import com.jobplatform.common.service.JobOutcomeService;
import com.jobplatform.manager.config.ManagerProperties;
import com.jobplatform.manager.metrics.ManagerMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class LeaseRecoveryService {

    private static final Logger log = LoggerFactory.getLogger(LeaseRecoveryService.class);
    private static final String EXPIRED_LEASE_MESSAGE = "lease expired: the worker did not report a result";

    private final JobRepository jobRepository;
    private final JobOutcomeService outcomeService;
    private final ManagerProperties properties;
    private final ManagerMetrics metrics;
    private final Clock clock;

    public LeaseRecoveryService(JobRepository jobRepository, JobOutcomeService outcomeService,
                                ManagerProperties properties, ManagerMetrics metrics, Clock clock) {
        this.jobRepository = jobRepository;
        this.outcomeService = outcomeService;
        this.properties = properties;
        this.metrics = metrics;
        this.clock = clock;
    }

    @Transactional
    public int recoverExpiredLeases() {
        LocalDateTime now = LocalDateTime.now(clock);
        List<Job> expired = jobRepository.lockExpiredLeases(now, properties.leaseRecoveryBatchSize());

        for (Job job : expired) {
            AttemptRef attempt = new AttemptRef(job.getId(), job.getWorkerId(), job.getAttemptCount(), job.getMaxRetries());
            long runningMs = Duration.between(job.getStartedAt(), now).toMillis();
            JobOutcome outcome = outcomeService.recordFailure(attempt, ExecutionStatus.ABANDONED,
                    EXPIRED_LEASE_MESSAGE, true, runningMs);
            metrics.leaseRecovered();
            log.warn("Job {} lease expired on worker {} attempt {} outcome={}", job.getId(), job.getWorkerId(),
                    job.getAttemptCount(), outcome);
        }
        return expired.size();
    }
}

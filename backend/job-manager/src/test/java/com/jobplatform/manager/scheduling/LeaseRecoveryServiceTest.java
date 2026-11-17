package com.jobplatform.manager.scheduling;

import com.jobplatform.common.domain.ExecutionStatus;
import com.jobplatform.common.domain.Job;
import com.jobplatform.common.domain.JobStatus;
import com.jobplatform.common.domain.JobType;
import com.jobplatform.common.repository.JobRepository;
import com.jobplatform.common.service.AttemptRef;
import com.jobplatform.common.service.JobOutcome;
import com.jobplatform.common.service.JobOutcomeService;
import com.jobplatform.manager.config.ManagerProperties;
import com.jobplatform.manager.metrics.ManagerMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LeaseRecoveryServiceTest {

    private static final Instant NOW_INSTANT = Instant.parse("2026-03-01T10:00:00Z");
    private static final LocalDateTime NOW = LocalDateTime.ofInstant(NOW_INSTANT, ZoneOffset.UTC);

    @Mock
    private JobRepository jobRepository;

    @Mock
    private JobOutcomeService outcomeService;

    private SimpleMeterRegistry registry;
    private LeaseRecoveryService service;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        ManagerProperties properties = new ManagerProperties(50, 60, 40, 20, 365, 5000);
        service = new LeaseRecoveryService(jobRepository, outcomeService, properties, new ManagerMetrics(registry),
                Clock.fixed(NOW_INSTANT, ZoneOffset.UTC));
    }

    private Job runningJob(long id, int attempt, int maxRetries) {
        Job job = Job.create("job", JobType.DATA_PROCESSING, "1,2", 5, null, NOW.minusMinutes(5), JobStatus.RUNNING,
                maxRetries, 30);
        ReflectionTestUtils.setField(job, "id", id);
        ReflectionTestUtils.setField(job, "workerId", "worker-2");
        ReflectionTestUtils.setField(job, "attemptCount", attempt);
        ReflectionTestUtils.setField(job, "startedAt", NOW.minusMinutes(2));
        ReflectionTestUtils.setField(job, "leaseExpiresAt", NOW.minusSeconds(10));
        return job;
    }

    @Test
    void expiredLeaseIsRecordedAsAnAbandonedRetryableAttempt() {
        when(jobRepository.lockExpiredLeases(NOW, 40)).thenReturn(List.of(runningJob(7, 1, 3)));
        when(outcomeService.recordFailure(org.mockito.ArgumentMatchers.any(), eq(ExecutionStatus.ABANDONED),
                org.mockito.ArgumentMatchers.anyString(), eq(true), anyLong())).thenReturn(JobOutcome.RETRY_SCHEDULED);

        int recovered = service.recoverExpiredLeases();

        assertThat(recovered).isEqualTo(1);
        verify(outcomeService).recordFailure(eq(new AttemptRef(7L, "worker-2", 1, 3)), eq(ExecutionStatus.ABANDONED),
                eq("lease expired: the worker did not report a result"), eq(true), eq(120_000L));
        assertThat(registry.counter("jobs.leases.recovered").count()).isEqualTo(1.0);
    }

    @Test
    void jobsWithoutExpiredLeasesAreLeftAlone() {
        when(jobRepository.lockExpiredLeases(NOW, 40)).thenReturn(List.of());

        assertThat(service.recoverExpiredLeases()).isZero();
        verify(outcomeService, never()).recordFailure(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), eq(true), anyLong());
    }
}

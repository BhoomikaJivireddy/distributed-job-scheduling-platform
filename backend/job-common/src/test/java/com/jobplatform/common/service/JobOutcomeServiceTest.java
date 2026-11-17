package com.jobplatform.common.service;

import com.jobplatform.common.config.RetryProperties;
import com.jobplatform.common.domain.ExecutionStatus;
import com.jobplatform.common.domain.JobStatus;
import com.jobplatform.common.repository.JobExecutionRepository;
import com.jobplatform.common.repository.JobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobOutcomeServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 1, 1, 0, 0);

    @Mock
    private JobRepository jobRepository;

    @Mock
    private JobExecutionRepository executionRepository;

    private JobOutcomeService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        service = new JobOutcomeService(jobRepository, executionRepository,
                new RetryPolicy(new RetryProperties(5, 300)), clock);
    }

    @Test
    void successCompletesTheJobAndTheExecution() {
        when(jobRepository.finishAttempt(1L, JobStatus.RUNNING, JobStatus.COMPLETED, "worker-1", 1, NOW, NOW, NOW, null))
                .thenReturn(1);

        JobOutcome outcome = service.recordSuccess(new AttemptRef(1L, "worker-1", 1, 3), "done", 120);

        assertThat(outcome).isEqualTo(JobOutcome.COMPLETED);
        verify(executionRepository).finish(1L, 1, ExecutionStatus.RUNNING, ExecutionStatus.COMPLETED, NOW, 120L, "done", null);
    }

    @Test
    void successFromAFencedWorkerIsDiscardedAsStale() {
        JobOutcome outcome = service.recordSuccess(new AttemptRef(1L, "worker-1", 1, 3), "done", 120);

        assertThat(outcome).isEqualTo(JobOutcome.STALE);
        verifyNoInteractions(executionRepository);
    }

    @Test
    void failureWithRetriesLeftSchedulesRetryWithExponentialBackoff() {
        when(jobRepository.finishAttempt(1L, JobStatus.RUNNING, JobStatus.RETRYING, "worker-1", 2, NOW, null,
                NOW.plusSeconds(10), "boom")).thenReturn(1);

        JobOutcome outcome = service.recordFailure(new AttemptRef(1L, "worker-1", 2, 3),
                ExecutionStatus.FAILED, "boom", true, 50);

        assertThat(outcome).isEqualTo(JobOutcome.RETRY_SCHEDULED);
        verify(executionRepository).finish(1L, 2, ExecutionStatus.RUNNING, ExecutionStatus.FAILED, NOW, 50L, null, "boom");
    }

    @Test
    void failureOnTheLastAllowedAttemptMarksTheJobDead() {
        when(jobRepository.finishAttempt(1L, JobStatus.RUNNING, JobStatus.DEAD, "worker-1", 4, NOW, NOW, NOW, "boom"))
                .thenReturn(1);

        JobOutcome outcome = service.recordFailure(new AttemptRef(1L, "worker-1", 4, 3),
                ExecutionStatus.TIMED_OUT, "boom", true, 50);

        assertThat(outcome).isEqualTo(JobOutcome.DEAD);
        verify(executionRepository).finish(1L, 4, ExecutionStatus.RUNNING, ExecutionStatus.TIMED_OUT, NOW, 50L, null, "boom");
    }

    @Test
    void jobWithZeroRetriesGoesDeadAfterTheFirstFailure() {
        when(jobRepository.finishAttempt(1L, JobStatus.RUNNING, JobStatus.DEAD, "worker-1", 1, NOW, NOW, NOW, "boom"))
                .thenReturn(1);

        JobOutcome outcome = service.recordFailure(new AttemptRef(1L, "worker-1", 1, 0),
                ExecutionStatus.FAILED, "boom", true, 5);

        assertThat(outcome).isEqualTo(JobOutcome.DEAD);
    }

    @Test
    void nonRetryableFailureEndsInFailedWithoutUsingRetries() {
        when(jobRepository.finishAttempt(1L, JobStatus.RUNNING, JobStatus.FAILED, "worker-1", 1, NOW, NOW, NOW, "bad payload"))
                .thenReturn(1);

        JobOutcome outcome = service.recordFailure(new AttemptRef(1L, "worker-1", 1, 5),
                ExecutionStatus.FAILED, "bad payload", false, 5);

        assertThat(outcome).isEqualTo(JobOutcome.FAILED);
    }

    @Test
    void failureFromAFencedWorkerIsDiscardedAsStale() {
        JobOutcome outcome = service.recordFailure(new AttemptRef(1L, "worker-1", 1, 3),
                ExecutionStatus.FAILED, "boom", true, 5);

        assertThat(outcome).isEqualTo(JobOutcome.STALE);
        verifyNoInteractions(executionRepository);
    }

    @Test
    void errorMessagesAreTruncatedToTheColumnLimit() {
        when(jobRepository.finishAttempt(anyLong(), any(), any(), anyString(), anyInt(), any(), any(), any(), anyString()))
                .thenReturn(1);
        ArgumentCaptor<String> error = ArgumentCaptor.forClass(String.class);

        service.recordFailure(new AttemptRef(1L, "worker-1", 1, 3), ExecutionStatus.FAILED, "x".repeat(5000), true, 5);

        verify(jobRepository).finishAttempt(eq(1L), eq(JobStatus.RUNNING), eq(JobStatus.RETRYING), eq("worker-1"), eq(1),
                eq(NOW), any(), any(), error.capture());
        assertThat(error.getValue()).hasSize(2000);
    }
}

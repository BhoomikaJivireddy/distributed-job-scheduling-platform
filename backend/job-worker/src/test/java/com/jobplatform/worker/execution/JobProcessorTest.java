package com.jobplatform.worker.execution;

import com.jobplatform.common.domain.ExecutionStatus;
import com.jobplatform.common.domain.Job;
import com.jobplatform.common.domain.JobStatus;
import com.jobplatform.common.domain.JobType;
import com.jobplatform.common.payload.InvalidPayloadException;
import com.jobplatform.common.repository.JobRepository;
import com.jobplatform.common.service.AttemptRef;
import com.jobplatform.common.service.JobOutcome;
import com.jobplatform.common.service.JobOutcomeService;
import com.jobplatform.common.service.JobStateService;
import com.jobplatform.worker.config.WorkerProperties;
import com.jobplatform.worker.handler.JobContext;
import com.jobplatform.worker.handler.JobHandler;
import com.jobplatform.worker.handler.JobHandlerRegistry;
import com.jobplatform.worker.handler.JobResult;
import com.jobplatform.worker.metrics.WorkerMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobProcessorTest {

    private static final Instant NOW_INSTANT = Instant.parse("2026-03-01T10:00:00Z");
    private static final LocalDateTime NOW = LocalDateTime.ofInstant(NOW_INSTANT, ZoneOffset.UTC);

    @Mock
    private JobRepository jobRepository;

    @Mock
    private JobStateService jobStateService;

    @Mock
    private JobOutcomeService outcomeService;

    @Mock
    private WorkerRegistry workerRegistry;

    private TimedExecutor timedExecutor;
    private SimpleMeterRegistry meterRegistry;
    private JobProcessor processor;
    private StubHandler handler;

    @BeforeEach
    void setUp() {
        WorkerProperties properties = new WorkerProperties("worker-1", 2, 30);
        timedExecutor = new TimedExecutor(properties);
        meterRegistry = new SimpleMeterRegistry();
        handler = new StubHandler();
        JobHandlerRegistry registry = new JobHandlerRegistry(java.util.List.of(
                handler,
                new NoopHandler(JobType.DATA_PROCESSING),
                new NoopHandler(JobType.REPORT_GENERATION),
                new NoopHandler(JobType.FAILURE_SIMULATION)));
        processor = new JobProcessor(jobRepository, jobStateService, outcomeService, registry, timedExecutor,
                workerRegistry, new WorkerMetrics(meterRegistry, workerRegistry), properties,
                Clock.fixed(NOW_INSTANT, ZoneOffset.UTC));
    }

    @AfterEach
    void tearDown() {
        timedExecutor.shutdown();
    }

    private Job job(long id, JobStatus status, int attempt, int timeoutSeconds) {
        Job job = Job.create("job", JobType.TEXT_PROCESSING, "payload", 5, null, NOW, status, 3, timeoutSeconds);
        ReflectionTestUtils.setField(job, "id", id);
        ReflectionTestUtils.setField(job, "attemptCount", attempt);
        return job;
    }

    private void claimSucceeds(Job queued, Job claimed) {
        when(jobRepository.findById(queued.getId())).thenReturn(Optional.of(queued));
        when(jobStateService.claim(queued.getId(), "worker-1", NOW, NOW.plusSeconds(queued.getTimeoutSeconds() + 30)))
                .thenReturn(Optional.of(claimed));
    }

    @Test
    void successfulExecutionCompletesTheJobAndCountsItOnTheWorker() {
        Job queued = job(1, JobStatus.QUEUED, 0, 10);
        claimSucceeds(queued, job(1, JobStatus.RUNNING, 1, 10));
        handler.behaviour = context -> new JobResult("ok");
        when(outcomeService.recordSuccess(any(AttemptRef.class), eq("ok"), anyLong())).thenReturn(JobOutcome.COMPLETED);

        ProcessingResult result = processor.process(1L);

        assertThat(result).isEqualTo(ProcessingResult.COMPLETED);
        verify(outcomeService).recordSuccess(eq(new AttemptRef(1L, "worker-1", 1, 3)), eq("ok"), anyLong());
        verify(workerRegistry).recordCompleted();
        verify(workerRegistry).executionStarted();
        verify(workerRegistry).executionFinished();
        assertThat(meterRegistry.counter("jobs.completed", "type", "TEXT_PROCESSING").count()).isEqualTo(1.0);
    }

    @Test
    void redeliveredMessageForAJobThatIsAlreadyRunningOrDoneIsDiscardedWithoutExecuting() {
        for (JobStatus status : new JobStatus[]{JobStatus.RUNNING, JobStatus.COMPLETED, JobStatus.CANCELLED, JobStatus.DEAD}) {
            when(jobRepository.findById(2L)).thenReturn(Optional.of(job(2, status, 1, 10)));

            assertThat(processor.process(2L)).isEqualTo(ProcessingResult.DISCARDED);
        }
        verifyNoInteractions(jobStateService, outcomeService);
        assertThat(handler.invocations).isZero();
    }

    @Test
    void messageForAMissingJobIsDiscarded() {
        when(jobRepository.findById(3L)).thenReturn(Optional.empty());

        assertThat(processor.process(3L)).isEqualTo(ProcessingResult.DISCARDED);
        verifyNoInteractions(jobStateService);
    }

    @Test
    void losingTheClaimRaceDiscardsTheMessageWithoutExecuting() {
        Job queued = job(4, JobStatus.QUEUED, 0, 10);
        when(jobRepository.findById(4L)).thenReturn(Optional.of(queued));
        when(jobStateService.claim(eq(4L), eq("worker-1"), any(), any())).thenReturn(Optional.empty());

        assertThat(processor.process(4L)).isEqualTo(ProcessingResult.DISCARDED);
        assertThat(handler.invocations).isZero();
        verifyNoInteractions(outcomeService);
    }

    @Test
    void handlerFailureIsRecordedAsRetryableAndAcknowledgedAsRetryScheduled() {
        Job queued = job(5, JobStatus.QUEUED, 0, 10);
        claimSucceeds(queued, job(5, JobStatus.RUNNING, 1, 10));
        handler.behaviour = context -> {
            throw new IllegalStateException("boom");
        };
        when(outcomeService.recordFailure(any(), eq(ExecutionStatus.FAILED), anyString(), eq(true), anyLong()))
                .thenReturn(JobOutcome.RETRY_SCHEDULED);

        ProcessingResult result = processor.process(5L);

        assertThat(result).isEqualTo(ProcessingResult.RETRY_SCHEDULED);
        verify(outcomeService).recordFailure(eq(new AttemptRef(5L, "worker-1", 1, 3)), eq(ExecutionStatus.FAILED),
                eq("IllegalStateException: boom"), eq(true), anyLong());
        verify(workerRegistry).recordFailed();
        assertThat(meterRegistry.counter("jobs.retries.scheduled", "type", "TEXT_PROCESSING").count()).isEqualTo(1.0);
    }

    @Test
    void exhaustedRetriesResultInDeadAndRequestDeadLettering() {
        Job queued = job(6, JobStatus.QUEUED, 3, 10);
        claimSucceeds(queued, job(6, JobStatus.RUNNING, 4, 10));
        handler.behaviour = context -> {
            throw new IllegalStateException("still failing");
        };
        when(outcomeService.recordFailure(any(), any(), anyString(), anyBoolean(), anyLong())).thenReturn(JobOutcome.DEAD);

        ProcessingResult result = processor.process(6L);

        assertThat(result).isEqualTo(ProcessingResult.DEAD);
        assertThat(result.requiresDeadLetter()).isTrue();
    }

    @Test
    void invalidPayloadIsNotRetried() {
        Job queued = job(7, JobStatus.QUEUED, 0, 10);
        claimSucceeds(queued, job(7, JobStatus.RUNNING, 1, 10));
        handler.behaviour = context -> {
            throw new InvalidPayloadException("bad csv");
        };
        when(outcomeService.recordFailure(any(), any(), eq("bad csv"), eq(false), anyLong())).thenReturn(JobOutcome.FAILED);

        assertThat(processor.process(7L)).isEqualTo(ProcessingResult.FAILED);
    }

    @Test
    void executionBeyondTheTimeoutIsInterruptedAndRecordedAsTimedOut() {
        Job queued = job(8, JobStatus.QUEUED, 0, 1);
        claimSucceeds(queued, job(8, JobStatus.RUNNING, 1, 1));
        handler.behaviour = context -> {
            Thread.sleep(10_000);
            return new JobResult("too late");
        };
        when(outcomeService.recordFailure(any(), eq(ExecutionStatus.TIMED_OUT), anyString(), eq(true), anyLong()))
                .thenReturn(JobOutcome.RETRY_SCHEDULED);

        long started = System.nanoTime();
        ProcessingResult result = processor.process(8L);
        long elapsedMillis = (System.nanoTime() - started) / 1_000_000;

        assertThat(result).isEqualTo(ProcessingResult.RETRY_SCHEDULED);
        assertThat(elapsedMillis).isLessThan(5_000);
        verify(outcomeService).recordFailure(any(), eq(ExecutionStatus.TIMED_OUT),
                eq("execution exceeded the timeout of 1 seconds"), eq(true), anyLong());
    }

    @Test
    void resultFromAWorkerWhoseLeaseWasRecoveredIsReportedAsStale() {
        Job queued = job(9, JobStatus.QUEUED, 0, 10);
        claimSucceeds(queued, job(9, JobStatus.RUNNING, 1, 10));
        handler.behaviour = context -> new JobResult("late");
        when(outcomeService.recordSuccess(any(), anyString(), anyLong())).thenReturn(JobOutcome.STALE);

        assertThat(processor.process(9L)).isEqualTo(ProcessingResult.STALE);
        verify(workerRegistry, never()).recordCompleted();
    }

    private interface Behaviour {
        JobResult run(JobContext context) throws Exception;
    }

    private static class StubHandler implements JobHandler {

        Behaviour behaviour = context -> new JobResult("default");
        int invocations;

        @Override
        public JobType jobType() {
            return JobType.TEXT_PROCESSING;
        }

        @Override
        public JobResult execute(JobContext context) throws Exception {
            invocations++;
            return behaviour.run(context);
        }
    }

    private record NoopHandler(JobType jobType) implements JobHandler {

        @Override
        public JobResult execute(JobContext context) {
            return new JobResult("noop");
        }
    }
}

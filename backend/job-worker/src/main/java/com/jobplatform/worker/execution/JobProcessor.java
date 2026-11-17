package com.jobplatform.worker.execution;

import com.jobplatform.common.domain.ExecutionStatus;
import com.jobplatform.common.domain.Job;
import com.jobplatform.common.domain.JobStatus;
import com.jobplatform.common.payload.NonRetryableJobException;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.TimeoutException;

@Component
public class JobProcessor {

    private static final Logger log = LoggerFactory.getLogger(JobProcessor.class);

    private final JobRepository jobRepository;
    private final JobStateService jobStateService;
    private final JobOutcomeService outcomeService;
    private final JobHandlerRegistry handlerRegistry;
    private final TimedExecutor timedExecutor;
    private final WorkerRegistry workerRegistry;
    private final WorkerMetrics metrics;
    private final WorkerProperties properties;
    private final Clock clock;

    public JobProcessor(JobRepository jobRepository, JobStateService jobStateService,
                        JobOutcomeService outcomeService, JobHandlerRegistry handlerRegistry,
                        TimedExecutor timedExecutor, WorkerRegistry workerRegistry, WorkerMetrics metrics,
                        WorkerProperties properties, Clock clock) {
        this.jobRepository = jobRepository;
        this.jobStateService = jobStateService;
        this.outcomeService = outcomeService;
        this.handlerRegistry = handlerRegistry;
        this.timedExecutor = timedExecutor;
        this.workerRegistry = workerRegistry;
        this.metrics = metrics;
        this.properties = properties;
        this.clock = clock;
    }

    public ProcessingResult process(Long jobId) {
        MDC.put("jobId", String.valueOf(jobId));
        MDC.put("workerId", properties.id());
        try {
            return processClaimedOrDiscard(jobId);
        } finally {
            MDC.remove("jobId");
            MDC.remove("workerId");
        }
    }

    private ProcessingResult processClaimedOrDiscard(Long jobId) {
        Optional<Job> loaded = jobRepository.findById(jobId);
        if (loaded.isEmpty()) {
            log.warn("Job {} does not exist; discarding message", jobId);
            metrics.duplicateDiscarded();
            return ProcessingResult.DISCARDED;
        }
        Job job = loaded.get();
        if (job.getStatus() != JobStatus.QUEUED) {
            log.info("Job {} is {}; message is a duplicate or outdated delivery and is discarded", jobId, job.getStatus());
            metrics.duplicateDiscarded();
            return ProcessingResult.DISCARDED;
        }

        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime leaseExpiresAt = now.plusSeconds(job.getTimeoutSeconds() + properties.leaseGraceSeconds());
        Optional<Job> claimed = jobStateService.claim(jobId, properties.id(), now, leaseExpiresAt);
        if (claimed.isEmpty()) {
            log.info("Job {} was claimed by another worker or is not yet due; message discarded", jobId);
            metrics.duplicateDiscarded();
            return ProcessingResult.DISCARDED;
        }

        return execute(claimed.get());
    }

    private ProcessingResult execute(Job job) {
        AttemptRef attempt = new AttemptRef(job.getId(), properties.id(), job.getAttemptCount(), job.getMaxRetries());
        log.info("Job {} claimed type={} attempt={}", job.getId(), job.getJobType(), attempt.attemptNumber());

        Optional<JobHandler> handler = handlerRegistry.find(job.getJobType());
        if (handler.isEmpty()) {
            return recordFailure(job, attempt, ExecutionStatus.FAILED, "no handler registered for " + job.getJobType(),
                    false, 0, "no_handler");
        }

        JobContext context = new JobContext(job.getId(), job.getJobType(), job.getPayload(), attempt.attemptNumber());
        Duration timeout = Duration.ofSeconds(job.getTimeoutSeconds());
        long startedNanos = System.nanoTime();
        workerRegistry.executionStarted();
        try {
            JobResult result = timedExecutor.execute(handler.get(), context, timeout);
            long durationMs = elapsedMillis(startedNanos);
            metrics.recordDuration(job.getJobType(), Duration.ofMillis(durationMs));
            return recordSuccess(job, attempt, result, durationMs);
        } catch (TimeoutException exception) {
            long durationMs = elapsedMillis(startedNanos);
            return recordFailure(job, attempt, ExecutionStatus.TIMED_OUT,
                    "execution exceeded the timeout of " + job.getTimeoutSeconds() + " seconds", true, durationMs, "timeout");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return recordFailure(job, attempt, ExecutionStatus.FAILED, "worker was interrupted during execution", true,
                    elapsedMillis(startedNanos), "interrupted");
        } catch (NonRetryableJobException exception) {
            return recordFailure(job, attempt, ExecutionStatus.FAILED, exception.getMessage(), false,
                    elapsedMillis(startedNanos), "non_retryable");
        } catch (Exception exception) {
            return recordFailure(job, attempt, ExecutionStatus.FAILED, describe(exception), true,
                    elapsedMillis(startedNanos), "error");
        } finally {
            workerRegistry.executionFinished();
        }
    }

    private ProcessingResult recordSuccess(Job job, AttemptRef attempt, JobResult result, long durationMs) {
        JobOutcome outcome = outcomeService.recordSuccess(attempt, result.summary(), durationMs);
        if (outcome == JobOutcome.STALE) {
            log.warn("Job {} finished after its lease was recovered; result discarded", job.getId());
            return ProcessingResult.STALE;
        }
        workerRegistry.recordCompleted();
        metrics.jobCompleted(job.getJobType());
        log.info("Job {} completed in {} ms", job.getId(), durationMs);
        return ProcessingResult.COMPLETED;
    }

    private ProcessingResult recordFailure(Job job, AttemptRef attempt, ExecutionStatus executionStatus, String error,
                                           boolean retryable, long durationMs, String reason) {
        JobOutcome outcome = outcomeService.recordFailure(attempt, executionStatus, error, retryable, durationMs);
        if (outcome == JobOutcome.STALE) {
            log.warn("Job {} failed after its lease was recovered; failure discarded", job.getId());
            return ProcessingResult.STALE;
        }
        workerRegistry.recordFailed();
        metrics.jobFailed(job.getJobType(), reason);
        log.warn("Job {} attempt {} failed reason={} outcome={}", job.getId(), attempt.attemptNumber(), reason, outcome);
        return switch (outcome) {
            case RETRY_SCHEDULED -> {
                metrics.retryScheduled(job.getJobType());
                yield ProcessingResult.RETRY_SCHEDULED;
            }
            case FAILED -> ProcessingResult.FAILED;
            case DEAD -> ProcessingResult.DEAD;
            case COMPLETED, STALE -> throw new IllegalStateException("Unexpected outcome " + outcome);
        };
    }

    private static long elapsedMillis(long startedNanos) {
        return Duration.ofNanos(System.nanoTime() - startedNanos).toMillis();
    }

    private static String describe(Exception exception) {
        String message = exception.getMessage();
        return message == null ? exception.getClass().getSimpleName()
                : exception.getClass().getSimpleName() + ": " + message;
    }
}

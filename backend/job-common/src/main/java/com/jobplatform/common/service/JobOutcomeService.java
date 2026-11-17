package com.jobplatform.common.service;

import com.jobplatform.common.domain.ExecutionStatus;
import com.jobplatform.common.domain.JobStatus;
import com.jobplatform.common.repository.JobExecutionRepository;
import com.jobplatform.common.repository.JobRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
public class JobOutcomeService {

    private static final int RESULT_LIMIT = 4000;
    private static final int ERROR_LIMIT = 2000;

    private final JobRepository jobRepository;
    private final JobExecutionRepository executionRepository;
    private final RetryPolicy retryPolicy;
    private final Clock clock;

    public JobOutcomeService(JobRepository jobRepository, JobExecutionRepository executionRepository,
                             RetryPolicy retryPolicy, Clock clock) {
        this.jobRepository = jobRepository;
        this.executionRepository = executionRepository;
        this.retryPolicy = retryPolicy;
        this.clock = clock;
    }

    @Transactional
    public JobOutcome recordSuccess(AttemptRef attempt, String result, long durationMs) {
        LocalDateTime now = LocalDateTime.now(clock);
        JobStatus.RUNNING.requireTransitionTo(JobStatus.COMPLETED);
        int updated = jobRepository.finishAttempt(attempt.jobId(), JobStatus.RUNNING, JobStatus.COMPLETED,
                attempt.workerId(), attempt.attemptNumber(), now, now, now, null);
        if (updated == 0) {
            return JobOutcome.STALE;
        }
        executionRepository.finish(attempt.jobId(), attempt.attemptNumber(), ExecutionStatus.RUNNING,
                ExecutionStatus.COMPLETED, now, durationMs, truncate(result, RESULT_LIMIT), null);
        return JobOutcome.COMPLETED;
    }

    @Transactional
    public JobOutcome recordFailure(AttemptRef attempt, ExecutionStatus executionStatus, String error,
                                    boolean retryable, long durationMs) {
        LocalDateTime now = LocalDateTime.now(clock);
        String message = truncate(error, ERROR_LIMIT);

        JobStatus target;
        JobOutcome outcome;
        LocalDateTime nextRunAt = now;
        LocalDateTime finishedAt = now;

        if (!retryable) {
            target = JobStatus.FAILED;
            outcome = JobOutcome.FAILED;
        } else if (attempt.attemptNumber() <= attempt.maxRetries()) {
            target = JobStatus.RETRYING;
            outcome = JobOutcome.RETRY_SCHEDULED;
            nextRunAt = now.plus(retryPolicy.delayAfterAttempt(attempt.attemptNumber()));
            finishedAt = null;
        } else {
            target = JobStatus.DEAD;
            outcome = JobOutcome.DEAD;
        }

        JobStatus.RUNNING.requireTransitionTo(target);
        int updated = jobRepository.finishAttempt(attempt.jobId(), JobStatus.RUNNING, target,
                attempt.workerId(), attempt.attemptNumber(), now, finishedAt, nextRunAt, message);
        if (updated == 0) {
            return JobOutcome.STALE;
        }
        executionRepository.finish(attempt.jobId(), attempt.attemptNumber(), ExecutionStatus.RUNNING,
                executionStatus, now, durationMs, null, message);
        return outcome;
    }

    private static String truncate(String value, int limit) {
        if (value == null || value.length() <= limit) {
            return value;
        }
        return value.substring(0, limit);
    }
}

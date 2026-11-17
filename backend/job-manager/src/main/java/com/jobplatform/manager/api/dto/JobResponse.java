package com.jobplatform.manager.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.jobplatform.common.domain.Job;
import com.jobplatform.common.domain.JobStatus;
import com.jobplatform.common.domain.JobType;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record JobResponse(
        Long id,
        String name,
        JobType jobType,
        JobStatus status,
        int priority,
        Instant scheduledAt,
        Instant nextRunAt,
        int maxRetries,
        int attemptCount,
        int retryCount,
        int timeoutSeconds,
        String workerId,
        Instant queuedAt,
        Instant startedAt,
        Instant finishedAt,
        Instant leaseExpiresAt,
        String lastError,
        String idempotencyKey,
        String payload,
        Instant createdAt,
        Instant updatedAt) {

    public static JobResponse summary(Job job) {
        return from(job, null, false);
    }

    public static JobResponse detail(Job job, String idempotencyKey) {
        return from(job, idempotencyKey, true);
    }

    private static JobResponse from(Job job, String idempotencyKey, boolean includePayload) {
        return new JobResponse(
                job.getId(),
                job.getName(),
                job.getJobType(),
                job.getStatus(),
                job.getPriority(),
                toInstant(job.getScheduledAt()),
                toInstant(job.getNextRunAt()),
                job.getMaxRetries(),
                job.getAttemptCount(),
                Math.max(job.getAttemptCount() - 1, 0),
                job.getTimeoutSeconds(),
                job.getWorkerId(),
                toInstant(job.getQueuedAt()),
                toInstant(job.getStartedAt()),
                toInstant(job.getFinishedAt()),
                toInstant(job.getLeaseExpiresAt()),
                job.getLastError(),
                idempotencyKey,
                includePayload ? job.getPayload() : null,
                toInstant(job.getCreatedAt()),
                toInstant(job.getUpdatedAt()));
    }

    static Instant toInstant(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }
}

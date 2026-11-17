package com.jobplatform.common.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(name = "jobs")
public class Job extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String name;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "job_type", nullable = false, length = 40)
    private JobType jobType;

    @Column(nullable = false, length = 10000)
    private String payload;

    @Column(nullable = false)
    private int priority;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private JobStatus status;

    @Column(name = "scheduled_at")
    private LocalDateTime scheduledAt;

    @Column(name = "next_run_at", nullable = false)
    private LocalDateTime nextRunAt;

    @Column(name = "max_retries", nullable = false)
    private int maxRetries;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "timeout_seconds", nullable = false)
    private int timeoutSeconds;

    @Column(name = "worker_id", length = 100)
    private String workerId;

    @Column(name = "queued_at")
    private LocalDateTime queuedAt;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @Column(name = "lease_expires_at")
    private LocalDateTime leaseExpiresAt;

    @Column(name = "last_error", length = 2000)
    private String lastError;

    protected Job() {
    }

    public static Job create(String name, JobType jobType, String payload, int priority,
                             LocalDateTime scheduledAt, LocalDateTime nextRunAt, JobStatus initialStatus,
                             int maxRetries, int timeoutSeconds) {
        Job job = new Job();
        job.name = name;
        job.jobType = jobType;
        job.payload = payload;
        job.priority = priority;
        job.scheduledAt = scheduledAt;
        job.nextRunAt = nextRunAt;
        job.status = initialStatus;
        job.maxRetries = maxRetries;
        job.timeoutSeconds = timeoutSeconds;
        job.attemptCount = 0;
        return job;
    }

    public void markQueued(LocalDateTime now) {
        status.requireTransitionTo(JobStatus.QUEUED);
        this.status = JobStatus.QUEUED;
        this.queuedAt = now;
    }

    public void refreshQueuedAt(LocalDateTime now) {
        if (status != JobStatus.QUEUED) {
            throw new IllegalStateException("Only queued jobs can be re-dispatched, but job " + id + " is " + status);
        }
        this.queuedAt = now;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public JobType getJobType() {
        return jobType;
    }

    public String getPayload() {
        return payload;
    }

    public int getPriority() {
        return priority;
    }

    public JobStatus getStatus() {
        return status;
    }

    public LocalDateTime getScheduledAt() {
        return scheduledAt;
    }

    public LocalDateTime getNextRunAt() {
        return nextRunAt;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public String getWorkerId() {
        return workerId;
    }

    public LocalDateTime getQueuedAt() {
        return queuedAt;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public LocalDateTime getFinishedAt() {
        return finishedAt;
    }

    public LocalDateTime getLeaseExpiresAt() {
        return leaseExpiresAt;
    }

    public String getLastError() {
        return lastError;
    }
}

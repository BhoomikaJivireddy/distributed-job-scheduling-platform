package com.jobplatform.common.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(name = "workers")
public class Worker extends Auditable {

    @Id
    @Column(length = 100)
    private String id;

    @Column(nullable = false)
    private String hostname;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private WorkerStatus status;

    @Column(nullable = false)
    private int concurrency;

    @Column(name = "current_executions", nullable = false)
    private int currentExecutions;

    @Column(name = "jobs_completed", nullable = false)
    private long jobsCompleted;

    @Column(name = "jobs_failed", nullable = false)
    private long jobsFailed;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "last_heartbeat_at", nullable = false)
    private LocalDateTime lastHeartbeatAt;

    protected Worker() {
    }

    public String getId() {
        return id;
    }

    public String getHostname() {
        return hostname;
    }

    public WorkerStatus getStatus() {
        return status;
    }

    public int getConcurrency() {
        return concurrency;
    }

    public int getCurrentExecutions() {
        return currentExecutions;
    }

    public long getJobsCompleted() {
        return jobsCompleted;
    }

    public long getJobsFailed() {
        return jobsFailed;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public LocalDateTime getLastHeartbeatAt() {
        return lastHeartbeatAt;
    }
}

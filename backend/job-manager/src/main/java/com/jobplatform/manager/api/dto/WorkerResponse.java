package com.jobplatform.manager.api.dto;

import com.jobplatform.common.domain.Worker;
import com.jobplatform.common.domain.WorkerStatus;

import java.time.Instant;
import java.time.LocalDateTime;

public record WorkerResponse(
        String id,
        String hostname,
        boolean active,
        int concurrency,
        int currentExecutions,
        long jobsCompleted,
        long jobsFailed,
        Instant startedAt,
        Instant lastHeartbeatAt) {

    public static WorkerResponse from(Worker worker, LocalDateTime heartbeatThreshold) {
        boolean active = worker.getStatus() == WorkerStatus.ACTIVE
                && worker.getLastHeartbeatAt().isAfter(heartbeatThreshold);
        return new WorkerResponse(
                worker.getId(),
                worker.getHostname(),
                active,
                worker.getConcurrency(),
                active ? worker.getCurrentExecutions() : 0,
                worker.getJobsCompleted(),
                worker.getJobsFailed(),
                JobResponse.toInstant(worker.getStartedAt()),
                JobResponse.toInstant(worker.getLastHeartbeatAt()));
    }
}

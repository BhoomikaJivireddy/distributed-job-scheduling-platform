package com.jobplatform.manager.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.jobplatform.common.domain.ExecutionStatus;
import com.jobplatform.common.domain.JobExecution;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ExecutionResponse(
        Long id,
        Long jobId,
        int attemptNumber,
        String workerId,
        ExecutionStatus status,
        Instant startedAt,
        Instant finishedAt,
        Long durationMs,
        String result,
        String errorMessage) {

    public static ExecutionResponse from(JobExecution execution) {
        return new ExecutionResponse(
                execution.getId(),
                execution.getJobId(),
                execution.getAttemptNumber(),
                execution.getWorkerId(),
                execution.getStatus(),
                JobResponse.toInstant(execution.getStartedAt()),
                JobResponse.toInstant(execution.getFinishedAt()),
                execution.getDurationMs(),
                execution.getResult(),
                execution.getErrorMessage());
    }
}

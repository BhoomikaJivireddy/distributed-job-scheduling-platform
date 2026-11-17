package com.jobplatform.worker.handler;

import com.jobplatform.common.domain.JobType;

public record JobContext(Long jobId, JobType jobType, String payload, int attemptNumber) {
}

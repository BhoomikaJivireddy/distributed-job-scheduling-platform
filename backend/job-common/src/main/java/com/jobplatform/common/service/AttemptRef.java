package com.jobplatform.common.service;

public record AttemptRef(Long jobId, String workerId, int attemptNumber, int maxRetries) {
}

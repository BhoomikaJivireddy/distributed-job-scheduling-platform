package com.jobplatform.common.service;

public enum JobOutcome {
    COMPLETED,
    RETRY_SCHEDULED,
    FAILED,
    DEAD,
    STALE
}

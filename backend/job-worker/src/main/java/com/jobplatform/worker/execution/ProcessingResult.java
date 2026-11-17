package com.jobplatform.worker.execution;

public enum ProcessingResult {
    COMPLETED,
    RETRY_SCHEDULED,
    FAILED,
    DEAD,
    STALE,
    DISCARDED;

    public boolean requiresDeadLetter() {
        return this == DEAD;
    }
}

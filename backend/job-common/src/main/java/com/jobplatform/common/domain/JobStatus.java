package com.jobplatform.common.domain;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public enum JobStatus {
    PENDING,
    SCHEDULED,
    QUEUED,
    RUNNING,
    COMPLETED,
    FAILED,
    RETRYING,
    CANCELLED,
    DEAD;

    public Set<JobStatus> allowedTargets() {
        return switch (this) {
            case PENDING, SCHEDULED, RETRYING -> EnumSet.of(QUEUED, CANCELLED);
            case QUEUED -> EnumSet.of(RUNNING, CANCELLED);
            case RUNNING -> EnumSet.of(COMPLETED, FAILED, RETRYING, DEAD);
            case COMPLETED, FAILED, CANCELLED, DEAD -> EnumSet.noneOf(JobStatus.class);
        };
    }

    public boolean canTransitionTo(JobStatus target) {
        return allowedTargets().contains(target);
    }

    public void requireTransitionTo(JobStatus target) {
        if (!canTransitionTo(target)) {
            throw new IllegalStateException("Invalid job status transition: " + this + " -> " + target);
        }
    }

    public boolean isTerminal() {
        return allowedTargets().isEmpty();
    }

    public static List<JobStatus> cancellable() {
        return Arrays.stream(values())
                .filter(status -> status.canTransitionTo(CANCELLED))
                .toList();
    }
}

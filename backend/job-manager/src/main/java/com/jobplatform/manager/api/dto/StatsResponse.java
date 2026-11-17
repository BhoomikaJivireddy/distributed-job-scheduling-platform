package com.jobplatform.manager.api.dto;

import java.util.Map;

public record StatsResponse(
        long totalJobs,
        long pendingJobs,
        long runningJobs,
        long completedJobs,
        long failedJobs,
        long activeWorkers,
        long inactiveWorkers,
        long totalRetries,
        long executionsLastHour,
        long averageCompletedDurationMs,
        Map<String, Long> jobsByStatus,
        Map<String, Long> jobsByType,
        Map<String, Long> executionsByStatus) {
}

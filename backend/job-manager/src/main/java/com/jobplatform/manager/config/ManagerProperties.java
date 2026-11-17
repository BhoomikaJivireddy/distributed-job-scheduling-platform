package com.jobplatform.manager.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "platform.manager")
public record ManagerProperties(
        int dispatchBatchSize,
        long queuedStaleSeconds,
        int leaseRecoveryBatchSize,
        long workerTimeoutSeconds,
        long maxScheduleDays,
        long publishConfirmTimeoutMs) {
}

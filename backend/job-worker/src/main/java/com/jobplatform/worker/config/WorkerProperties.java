package com.jobplatform.worker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "platform.worker")
public record WorkerProperties(String id, int concurrency, long leaseGraceSeconds) {
}

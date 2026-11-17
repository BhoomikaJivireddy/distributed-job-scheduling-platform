package com.jobplatform.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "platform.retry")
public record RetryProperties(long baseDelaySeconds, long maxDelaySeconds) {
}

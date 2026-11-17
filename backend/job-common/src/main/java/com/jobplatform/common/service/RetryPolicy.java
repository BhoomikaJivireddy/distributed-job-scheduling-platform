package com.jobplatform.common.service;

import com.jobplatform.common.config.RetryProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class RetryPolicy {

    private static final int MAX_EXPONENT = 20;

    private final RetryProperties properties;

    public RetryPolicy(RetryProperties properties) {
        this.properties = properties;
    }

    public Duration delayAfterAttempt(int attemptNumber) {
        int exponent = Math.min(Math.max(attemptNumber - 1, 0), MAX_EXPONENT);
        long seconds = Math.min(properties.baseDelaySeconds() * (1L << exponent), properties.maxDelaySeconds());
        return Duration.ofSeconds(seconds);
    }
}

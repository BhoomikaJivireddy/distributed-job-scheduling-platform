package com.jobplatform.common.service;

import com.jobplatform.common.config.RetryProperties;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class RetryPolicyTest {

    private final RetryPolicy policy = new RetryPolicy(new RetryProperties(5, 300));

    @Test
    void delayDoublesWithEveryAttempt() {
        assertThat(policy.delayAfterAttempt(1)).isEqualTo(Duration.ofSeconds(5));
        assertThat(policy.delayAfterAttempt(2)).isEqualTo(Duration.ofSeconds(10));
        assertThat(policy.delayAfterAttempt(3)).isEqualTo(Duration.ofSeconds(20));
        assertThat(policy.delayAfterAttempt(4)).isEqualTo(Duration.ofSeconds(40));
    }

    @Test
    void delayNeverExceedsTheConfiguredMaximum() {
        assertThat(policy.delayAfterAttempt(7)).isEqualTo(Duration.ofSeconds(300));
        assertThat(policy.delayAfterAttempt(60)).isEqualTo(Duration.ofSeconds(300));
    }

    @Test
    void attemptNumbersBelowOneUseTheBaseDelay() {
        assertThat(policy.delayAfterAttempt(0)).isEqualTo(Duration.ofSeconds(5));
    }
}

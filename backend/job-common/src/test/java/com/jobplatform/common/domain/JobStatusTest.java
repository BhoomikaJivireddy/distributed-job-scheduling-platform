package com.jobplatform.common.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JobStatusTest {

    @Test
    void pendingJobsCanOnlyBeQueuedOrCancelled() {
        assertThat(JobStatus.PENDING.allowedTargets()).containsExactlyInAnyOrder(JobStatus.QUEUED, JobStatus.CANCELLED);
        assertThat(JobStatus.SCHEDULED.allowedTargets()).containsExactlyInAnyOrder(JobStatus.QUEUED, JobStatus.CANCELLED);
        assertThat(JobStatus.RETRYING.allowedTargets()).containsExactlyInAnyOrder(JobStatus.QUEUED, JobStatus.CANCELLED);
    }

    @Test
    void queuedJobsCanBeClaimedOrCancelled() {
        assertThat(JobStatus.QUEUED.allowedTargets()).containsExactlyInAnyOrder(JobStatus.RUNNING, JobStatus.CANCELLED);
    }

    @Test
    void runningJobsCanOnlyFinishFailOrRetry() {
        assertThat(JobStatus.RUNNING.allowedTargets())
                .containsExactlyInAnyOrder(JobStatus.COMPLETED, JobStatus.FAILED, JobStatus.RETRYING, JobStatus.DEAD);
        assertThat(JobStatus.RUNNING.canTransitionTo(JobStatus.CANCELLED)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = JobStatus.class, names = {"COMPLETED", "FAILED", "CANCELLED", "DEAD"})
    void terminalStatusesHaveNoTransitions(JobStatus status) {
        assertThat(status.isTerminal()).isTrue();
        assertThat(status.allowedTargets()).isEmpty();
    }

    @Test
    void invalidTransitionIsRejected() {
        assertThatThrownBy(() -> JobStatus.COMPLETED.requireTransitionTo(JobStatus.RUNNING))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("COMPLETED -> RUNNING");
        assertThatThrownBy(() -> JobStatus.PENDING.requireTransitionTo(JobStatus.RUNNING))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void cancellableStatusesAreTheOnesThatHaveNotStartedRunning() {
        assertThat(JobStatus.cancellable())
                .containsExactlyInAnyOrder(JobStatus.PENDING, JobStatus.SCHEDULED, JobStatus.QUEUED, JobStatus.RETRYING);
    }
}

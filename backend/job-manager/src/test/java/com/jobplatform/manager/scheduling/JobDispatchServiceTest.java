package com.jobplatform.manager.scheduling;

import com.jobplatform.common.domain.Job;
import com.jobplatform.common.domain.JobStatus;
import com.jobplatform.common.domain.JobType;
import com.jobplatform.common.repository.JobRepository;
import com.jobplatform.manager.config.ManagerProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobDispatchServiceTest {

    private static final Instant NOW_INSTANT = Instant.parse("2026-03-01T10:00:00Z");
    private static final LocalDateTime NOW = LocalDateTime.ofInstant(NOW_INSTANT, ZoneOffset.UTC);

    @Mock
    private JobRepository jobRepository;

    private JobDispatchService service;

    @BeforeEach
    void setUp() {
        ManagerProperties properties = new ManagerProperties(25, 60, 50, 20, 365, 5000);
        service = new JobDispatchService(jobRepository, properties, Clock.fixed(NOW_INSTANT, ZoneOffset.UTC));
    }

    private Job job(long id, JobStatus status, int priority) {
        Job job = Job.create("job", JobType.TEXT_PROCESSING, "text", priority, null, NOW.minusSeconds(5), status, 3, 60);
        ReflectionTestUtils.setField(job, "id", id);
        return job;
    }

    @Test
    void dueJobsAreLockedMarkedQueuedAndReturnedForPublishing() {
        Job scheduled = job(1, JobStatus.SCHEDULED, 9);
        Job retrying = job(2, JobStatus.RETRYING, 3);
        when(jobRepository.lockDispatchable(NOW, NOW.minusSeconds(60), 25)).thenReturn(List.of(scheduled, retrying));

        List<DispatchTicket> tickets = service.reserveDueJobs();

        assertThat(tickets).containsExactly(new DispatchTicket(1L, 9, false), new DispatchTicket(2L, 3, false));
        assertThat(scheduled.getStatus()).isEqualTo(JobStatus.QUEUED);
        assertThat(retrying.getStatus()).isEqualTo(JobStatus.QUEUED);
        assertThat(scheduled.getQueuedAt()).isEqualTo(NOW);
    }

    @Test
    void jobsStuckInQueuedAreRedispatchedWithAFreshQueuedTimestamp() {
        Job stuck = job(3, JobStatus.QUEUED, 5);
        ReflectionTestUtils.setField(stuck, "queuedAt", NOW.minusSeconds(600));
        when(jobRepository.lockDispatchable(NOW, NOW.minusSeconds(60), 25)).thenReturn(List.of(stuck));

        List<DispatchTicket> tickets = service.reserveDueJobs();

        assertThat(tickets).containsExactly(new DispatchTicket(3L, 5, true));
        assertThat(stuck.getStatus()).isEqualTo(JobStatus.QUEUED);
        assertThat(stuck.getQueuedAt()).isEqualTo(NOW);
    }

    @Test
    void nothingDueProducesNoTickets() {
        when(jobRepository.lockDispatchable(NOW, NOW.minusSeconds(60), 25)).thenReturn(List.of());

        assertThat(service.reserveDueJobs()).isEmpty();
        verify(jobRepository).lockDispatchable(NOW, NOW.minusSeconds(60), 25);
    }
}

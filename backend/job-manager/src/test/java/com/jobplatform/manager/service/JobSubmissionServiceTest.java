package com.jobplatform.manager.service;

import com.jobplatform.common.domain.IdempotencyRecord;
import com.jobplatform.common.domain.Job;
import com.jobplatform.common.domain.JobStatus;
import com.jobplatform.common.domain.JobType;
import com.jobplatform.common.repository.IdempotencyRecordRepository;
import com.jobplatform.common.repository.JobRepository;
import com.jobplatform.manager.api.dto.CreateJobRequest;
import com.jobplatform.manager.config.ManagerProperties;
import com.jobplatform.manager.metrics.ManagerMetrics;
import com.jobplatform.manager.service.JobSubmissionService.SubmissionResult;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobSubmissionServiceTest {

    private static final Instant NOW = Instant.parse("2026-03-01T10:00:00Z");

    @Mock
    private JobRepository jobRepository;

    @Mock
    private IdempotencyRecordRepository idempotencyRepository;

    private JobSubmissionService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        TransactionTemplate transactionTemplate = new TransactionTemplate(new NoOpTransactionManager());
        ManagerProperties properties = new ManagerProperties(50, 60, 50, 20, 365, 5000);
        service = new JobSubmissionService(jobRepository, idempotencyRepository, transactionTemplate, properties,
                new ManagerMetrics(new SimpleMeterRegistry()), clock);
    }

    private CreateJobRequest request(Instant scheduledAt, String key) {
        return new CreateJobRequest("word count", JobType.TEXT_PROCESSING, "hello world", 7, scheduledAt, 2, 30, key);
    }

    private void stubSave() {
        when(jobRepository.saveAndFlush(any(Job.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void jobWithoutScheduleIsCreatedPendingAndDueNow() {
        stubSave();

        SubmissionResult result = service.submit(request(null, null));

        assertThat(result.created()).isTrue();
        Job job = result.job();
        assertThat(job.getStatus()).isEqualTo(JobStatus.PENDING);
        assertThat(job.getNextRunAt()).isEqualTo(LocalDateTime.ofInstant(NOW, ZoneOffset.UTC));
        assertThat(job.getPriority()).isEqualTo(7);
        assertThat(job.getMaxRetries()).isEqualTo(2);
        assertThat(job.getTimeoutSeconds()).isEqualTo(30);
        assertThat(job.getAttemptCount()).isZero();
        verify(idempotencyRepository, never()).saveAndFlush(any());
    }

    @Test
    void futureJobIsCreatedScheduledAndNotDueUntilItsTime() {
        stubSave();
        Instant later = NOW.plusSeconds(3600);

        Job job = service.submit(request(later, null)).job();

        assertThat(job.getStatus()).isEqualTo(JobStatus.SCHEDULED);
        assertThat(job.getNextRunAt()).isEqualTo(LocalDateTime.ofInstant(later, ZoneOffset.UTC));
        assertThat(job.getScheduledAt()).isEqualTo(job.getNextRunAt());
    }

    @Test
    void scheduledTimeInThePastIsRejected() {
        assertThatThrownBy(() -> service.submit(request(NOW.minusSeconds(3600), null)))
                .isInstanceOf(InvalidJobRequestException.class)
                .hasMessageContaining("in the past");
        verify(jobRepository, never()).saveAndFlush(any());
    }

    @Test
    void scheduledTimeTooFarInTheFutureIsRejected() {
        assertThatThrownBy(() -> service.submit(request(NOW.plusSeconds(400L * 24 * 3600), null)))
                .isInstanceOf(InvalidJobRequestException.class)
                .hasMessageContaining("within 365 days");
    }

    @Test
    void payloadThatDoesNotMatchTheJobTypeIsRejected() {
        CreateJobRequest invalid = new CreateJobRequest("numbers", JobType.DATA_PROCESSING, "1, 2, three",
                null, null, null, null, null);

        assertThatThrownBy(() -> service.submit(invalid))
                .isInstanceOf(InvalidJobRequestException.class)
                .hasMessageContaining("non-numeric");
        verify(jobRepository, never()).saveAndFlush(any());
    }

    @Test
    void defaultsAreAppliedWhenOptionalFieldsAreMissing() {
        stubSave();
        CreateJobRequest minimal = new CreateJobRequest("minimal", JobType.TEXT_PROCESSING, "text", null, null, null, null, null);

        Job job = service.submit(minimal).job();

        assertThat(job.getPriority()).isEqualTo(5);
        assertThat(job.getMaxRetries()).isEqualTo(3);
        assertThat(job.getTimeoutSeconds()).isEqualTo(60);
    }

    @Test
    void newIdempotencyKeyStoresARecordNextToTheJob() {
        stubSave();
        when(idempotencyRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.empty());

        SubmissionResult result = service.submit(request(null, "key-1"));

        assertThat(result.created()).isTrue();
        ArgumentCaptor<IdempotencyRecord> record = ArgumentCaptor.forClass(IdempotencyRecord.class);
        verify(idempotencyRepository).saveAndFlush(record.capture());
        assertThat(record.getValue().getIdempotencyKey()).isEqualTo("key-1");
        assertThat(record.getValue().getRequestHash()).hasSize(64);
    }

    @Test
    void repeatedKeyWithTheSameRequestReturnsTheExistingJobWithoutCreatingAnother() {
        Job existing = Job.create("word count", JobType.TEXT_PROCESSING, "hello world", 7, null,
                LocalDateTime.ofInstant(NOW, ZoneOffset.UTC), JobStatus.PENDING, 2, 30);
        String hash = JobSubmissionService.hashRequest(request(null, "key-1"), 7, 2, 30);
        when(idempotencyRepository.findByIdempotencyKey("key-1"))
                .thenReturn(Optional.of(new IdempotencyRecord("key-1", hash, 42L)));
        when(jobRepository.findById(42L)).thenReturn(Optional.of(existing));

        SubmissionResult result = service.submit(request(null, "key-1"));

        assertThat(result.created()).isFalse();
        assertThat(result.job()).isSameAs(existing);
        verify(jobRepository, never()).saveAndFlush(any());
    }

    @Test
    void repeatedKeyWithADifferentRequestIsAConflict() {
        when(idempotencyRepository.findByIdempotencyKey("key-1"))
                .thenReturn(Optional.of(new IdempotencyRecord("key-1", "different-hash", 42L)));

        assertThatThrownBy(() -> service.submit(request(null, "key-1")))
                .isInstanceOf(IdempotencyConflictException.class);
        verify(jobRepository, never()).saveAndFlush(any());
    }

    @Test
    void concurrentDuplicateLosingTheUniqueConstraintRaceReturnsTheWinner() {
        Job winner = Job.create("word count", JobType.TEXT_PROCESSING, "hello world", 7, null,
                LocalDateTime.ofInstant(NOW, ZoneOffset.UTC), JobStatus.PENDING, 2, 30);
        String hash = JobSubmissionService.hashRequest(request(null, "key-1"), 7, 2, 30);
        when(idempotencyRepository.findByIdempotencyKey("key-1"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(new IdempotencyRecord("key-1", hash, 42L)));
        when(jobRepository.saveAndFlush(any(Job.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(idempotencyRepository.saveAndFlush(any(IdempotencyRecord.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));
        when(jobRepository.findById(42L)).thenReturn(Optional.of(winner));

        SubmissionResult result = service.submit(request(null, "key-1"));

        assertThat(result.created()).isFalse();
        assertThat(result.job()).isSameAs(winner);
    }
}

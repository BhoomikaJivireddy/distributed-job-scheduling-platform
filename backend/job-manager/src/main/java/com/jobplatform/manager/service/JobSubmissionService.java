package com.jobplatform.manager.service;

import com.jobplatform.common.domain.IdempotencyRecord;
import com.jobplatform.common.domain.Job;
import com.jobplatform.common.domain.JobStatus;
import com.jobplatform.common.payload.InvalidPayloadException;
import com.jobplatform.common.payload.PayloadValidator;
import com.jobplatform.common.repository.IdempotencyRecordRepository;
import com.jobplatform.common.repository.JobRepository;
import com.jobplatform.manager.api.dto.CreateJobRequest;
import com.jobplatform.manager.config.ManagerProperties;
import com.jobplatform.manager.metrics.ManagerMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Optional;

@Service
public class JobSubmissionService {

    private static final Logger log = LoggerFactory.getLogger(JobSubmissionService.class);

    private static final int DEFAULT_PRIORITY = 5;
    private static final int DEFAULT_MAX_RETRIES = 3;
    private static final int DEFAULT_TIMEOUT_SECONDS = 60;
    private static final long PAST_TOLERANCE_SECONDS = 60;

    private final JobRepository jobRepository;
    private final IdempotencyRecordRepository idempotencyRepository;
    private final TransactionTemplate transactionTemplate;
    private final ManagerProperties properties;
    private final ManagerMetrics metrics;
    private final Clock clock;

    public JobSubmissionService(JobRepository jobRepository, IdempotencyRecordRepository idempotencyRepository,
                                TransactionTemplate transactionTemplate, ManagerProperties properties,
                                ManagerMetrics metrics, Clock clock) {
        this.jobRepository = jobRepository;
        this.idempotencyRepository = idempotencyRepository;
        this.transactionTemplate = transactionTemplate;
        this.properties = properties;
        this.metrics = metrics;
        this.clock = clock;
    }

    public SubmissionResult submit(CreateJobRequest request) {
        validatePayload(request);

        LocalDateTime now = LocalDateTime.now(clock);
        int priority = request.priority() != null ? request.priority() : DEFAULT_PRIORITY;
        int maxRetries = request.maxRetries() != null ? request.maxRetries() : DEFAULT_MAX_RETRIES;
        int timeoutSeconds = request.timeoutSeconds() != null ? request.timeoutSeconds() : DEFAULT_TIMEOUT_SECONDS;
        String idempotencyKey = normalizeKey(request.idempotencyKey());
        String requestHash = hashRequest(request, priority, maxRetries, timeoutSeconds);

        if (idempotencyKey != null) {
            Optional<Job> existing = findExisting(idempotencyKey, requestHash);
            if (existing.isPresent()) {
                return new SubmissionResult(existing.get(), false);
            }
        }

        LocalDateTime scheduledAt = resolveScheduledAt(request, now);
        boolean future = scheduledAt != null && scheduledAt.isAfter(now);
        LocalDateTime nextRunAt = future ? scheduledAt : now;
        JobStatus initialStatus = future ? JobStatus.SCHEDULED : JobStatus.PENDING;

        Job newJob = Job.create(request.name().strip(), request.jobType(), request.payload(), priority,
                scheduledAt, nextRunAt, initialStatus, maxRetries, timeoutSeconds);

        try {
            Job saved = transactionTemplate.execute(status -> persist(newJob, idempotencyKey, requestHash));
            metrics.jobSubmitted(saved.getJobType());
            log.info("Job {} accepted type={} status={} priority={}", saved.getId(), saved.getJobType(),
                    saved.getStatus(), saved.getPriority());
            return new SubmissionResult(saved, true);
        } catch (DataIntegrityViolationException exception) {
            if (idempotencyKey == null) {
                throw exception;
            }
            Job winner = findExisting(idempotencyKey, requestHash).orElseThrow(() -> exception);
            log.info("Job {} returned for concurrent duplicate submission", winner.getId());
            return new SubmissionResult(winner, false);
        }
    }

    private Job persist(Job newJob, String idempotencyKey, String requestHash) {
        Job saved = jobRepository.saveAndFlush(newJob);
        if (idempotencyKey != null) {
            idempotencyRepository.saveAndFlush(new IdempotencyRecord(idempotencyKey, requestHash, saved.getId()));
        }
        return saved;
    }

    private Optional<Job> findExisting(String idempotencyKey, String requestHash) {
        return idempotencyRepository.findByIdempotencyKey(idempotencyKey).map(record -> {
            if (!record.getRequestHash().equals(requestHash)) {
                throw new IdempotencyConflictException(idempotencyKey);
            }
            return jobRepository.findById(record.getJobId()).orElseThrow(() -> new JobNotFoundException(record.getJobId()));
        });
    }

    private void validatePayload(CreateJobRequest request) {
        try {
            PayloadValidator.validate(request.jobType(), request.payload());
        } catch (InvalidPayloadException exception) {
            throw new InvalidJobRequestException(exception.getMessage());
        }
    }

    private LocalDateTime resolveScheduledAt(CreateJobRequest request, LocalDateTime now) {
        if (request.scheduledAt() == null) {
            return null;
        }
        LocalDateTime scheduledAt = LocalDateTime.ofInstant(request.scheduledAt(), ZoneOffset.UTC);
        if (scheduledAt.isBefore(now.minusSeconds(PAST_TOLERANCE_SECONDS))) {
            throw new InvalidJobRequestException("scheduledAt is in the past");
        }
        if (scheduledAt.isAfter(now.plusDays(properties.maxScheduleDays()))) {
            throw new InvalidJobRequestException(
                    "scheduledAt must be within " + properties.maxScheduleDays() + " days from now");
        }
        return scheduledAt;
    }

    private static String normalizeKey(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        return key.strip();
    }

    static String hashRequest(CreateJobRequest request, int priority, int maxRetries, int timeoutSeconds) {
        String canonical = String.join("\u0000",
                request.name().strip(),
                request.jobType().name(),
                request.payload(),
                String.valueOf(priority),
                request.scheduledAt() == null ? "" : request.scheduledAt().toString(),
                String.valueOf(maxRetries),
                String.valueOf(timeoutSeconds));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    public record SubmissionResult(Job job, boolean created) {
    }
}

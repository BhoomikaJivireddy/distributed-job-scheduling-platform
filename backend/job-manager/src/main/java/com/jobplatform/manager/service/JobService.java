package com.jobplatform.manager.service;

import com.jobplatform.common.domain.Job;
import com.jobplatform.common.domain.JobStatus;
import com.jobplatform.common.domain.JobType;
import com.jobplatform.common.repository.IdempotencyRecordRepository;
import com.jobplatform.common.repository.JobExecutionRepository;
import com.jobplatform.common.repository.JobRepository;
import com.jobplatform.common.service.JobStateService;
import com.jobplatform.manager.api.dto.ExecutionResponse;
import com.jobplatform.manager.api.dto.JobResponse;
import com.jobplatform.manager.api.dto.PageResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Service
public class JobService {

    private static final Logger log = LoggerFactory.getLogger(JobService.class);

    private static final Set<String> SORTABLE_FIELDS =
            Set.of("id", "name", "jobType", "status", "priority", "createdAt", "updatedAt", "startedAt", "nextRunAt");
    private static final int MAX_PAGE_SIZE = 100;

    private final JobRepository jobRepository;
    private final JobExecutionRepository executionRepository;
    private final IdempotencyRecordRepository idempotencyRepository;
    private final JobStateService jobStateService;
    private final Clock clock;

    public JobService(JobRepository jobRepository, JobExecutionRepository executionRepository,
                      IdempotencyRecordRepository idempotencyRepository, JobStateService jobStateService,
                      Clock clock) {
        this.jobRepository = jobRepository;
        this.executionRepository = executionRepository;
        this.idempotencyRepository = idempotencyRepository;
        this.jobStateService = jobStateService;
        this.clock = clock;
    }

    public PageResponse<JobResponse> list(JobStatus status, JobType jobType, int page, int size, String sort) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE), parseSort(sort));
        Page<Job> jobs;
        if (status != null && jobType != null) {
            jobs = jobRepository.findByStatusAndJobType(status, jobType, pageable);
        } else if (status != null) {
            jobs = jobRepository.findByStatus(status, pageable);
        } else if (jobType != null) {
            jobs = jobRepository.findByJobType(jobType, pageable);
        } else {
            jobs = jobRepository.findAll(pageable);
        }
        return PageResponse.from(jobs, JobResponse::summary);
    }

    public JobResponse get(Long jobId) {
        Job job = findJob(jobId);
        String idempotencyKey = idempotencyRepository.findByJobId(jobId)
                .map(record -> record.getIdempotencyKey())
                .orElse(null);
        return JobResponse.detail(job, idempotencyKey);
    }

    public List<ExecutionResponse> executions(Long jobId) {
        findJob(jobId);
        return executionRepository.findByJobIdOrderByAttemptNumberAsc(jobId).stream()
                .map(ExecutionResponse::from)
                .toList();
    }

    public JobResponse cancel(Long jobId) {
        Job job = findJob(jobId);
        LocalDateTime now = LocalDateTime.now(clock);
        if (jobStateService.cancel(jobId, now)) {
            log.info("Job {} cancelled from status {}", jobId, job.getStatus());
            return get(jobId);
        }
        JobStatus current = findJob(jobId).getStatus();
        if (current == JobStatus.RUNNING) {
            throw new JobStateConflictException(
                    "Job " + jobId + " is running; running jobs cannot be cancelled and end on completion or timeout");
        }
        throw new JobStateConflictException("Job " + jobId + " is " + current + " and cannot be cancelled");
    }

    public Job findJob(Long jobId) {
        return jobRepository.findById(jobId).orElseThrow(() -> new JobNotFoundException(jobId));
    }

    private Sort parseSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return Sort.by(Sort.Direction.DESC, "createdAt");
        }
        String[] parts = sort.split(",");
        String field = parts[0].strip();
        if (!SORTABLE_FIELDS.contains(field)) {
            throw new InvalidJobRequestException("sort field must be one of " + SORTABLE_FIELDS.stream().sorted().toList());
        }
        Sort.Direction direction = Sort.Direction.DESC;
        if (parts.length > 1) {
            direction = Sort.Direction.fromOptionalString(parts[1].strip())
                    .orElseThrow(() -> new InvalidJobRequestException("sort direction must be asc or desc"));
        }
        return Sort.by(direction, field).and(Sort.by(Sort.Direction.DESC, "id"));
    }
}

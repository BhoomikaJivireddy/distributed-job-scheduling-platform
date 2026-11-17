package com.jobplatform.common.service;

import com.jobplatform.common.domain.Job;
import com.jobplatform.common.domain.JobExecution;
import com.jobplatform.common.domain.JobStatus;
import com.jobplatform.common.repository.JobExecutionRepository;
import com.jobplatform.common.repository.JobRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class JobStateService {

    private final JobRepository jobRepository;
    private final JobExecutionRepository executionRepository;

    public JobStateService(JobRepository jobRepository, JobExecutionRepository executionRepository) {
        this.jobRepository = jobRepository;
        this.executionRepository = executionRepository;
    }

    @Transactional
    public Optional<Job> claim(Long jobId, String workerId, LocalDateTime now, LocalDateTime leaseExpiresAt) {
        JobStatus.QUEUED.requireTransitionTo(JobStatus.RUNNING);
        int claimed = jobRepository.claim(jobId, JobStatus.QUEUED, JobStatus.RUNNING, workerId, now, leaseExpiresAt);
        if (claimed == 0) {
            return Optional.empty();
        }
        Job job = jobRepository.findById(jobId).orElseThrow();
        executionRepository.save(JobExecution.started(jobId, job.getAttemptCount(), workerId, now));
        return Optional.of(job);
    }

    @Transactional
    public boolean cancel(Long jobId, LocalDateTime now) {
        return jobRepository.cancel(jobId, JobStatus.cancellable(), JobStatus.CANCELLED, now) == 1;
    }
}

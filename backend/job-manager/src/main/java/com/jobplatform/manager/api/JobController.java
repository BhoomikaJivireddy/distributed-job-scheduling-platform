package com.jobplatform.manager.api;

import com.jobplatform.common.domain.JobStatus;
import com.jobplatform.common.domain.JobType;
import com.jobplatform.manager.api.dto.CreateJobRequest;
import com.jobplatform.manager.api.dto.ExecutionResponse;
import com.jobplatform.manager.api.dto.JobResponse;
import com.jobplatform.manager.api.dto.PageResponse;
import com.jobplatform.manager.service.JobService;
import com.jobplatform.manager.service.JobSubmissionService;
import com.jobplatform.manager.service.JobSubmissionService.SubmissionResult;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/jobs")
public class JobController {

    private final JobSubmissionService submissionService;
    private final JobService jobService;

    public JobController(JobSubmissionService submissionService, JobService jobService) {
        this.submissionService = submissionService;
        this.jobService = jobService;
    }

    @PostMapping
    public ResponseEntity<JobResponse> createJob(@Valid @RequestBody CreateJobRequest request) {
        SubmissionResult result = submissionService.submit(request);
        JobResponse body = jobService.get(result.job().getId());
        if (result.created()) {
            return ResponseEntity.status(HttpStatus.CREATED).body(body);
        }
        return ResponseEntity.ok().header("Idempotent-Replayed", "true").body(body);
    }

    @GetMapping
    public PageResponse<JobResponse> listJobs(
            @RequestParam(required = false) JobStatus status,
            @RequestParam(required = false) JobType jobType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort) {
        return jobService.list(status, jobType, page, size, sort);
    }

    @GetMapping("/{jobId}")
    public JobResponse getJob(@PathVariable Long jobId) {
        return jobService.get(jobId);
    }

    @GetMapping("/{jobId}/executions")
    public List<ExecutionResponse> getExecutions(@PathVariable Long jobId) {
        return jobService.executions(jobId);
    }

    @PostMapping("/{jobId}/cancel")
    public JobResponse cancelJob(@PathVariable Long jobId) {
        return jobService.cancel(jobId);
    }
}

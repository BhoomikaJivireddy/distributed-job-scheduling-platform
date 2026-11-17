package com.jobplatform.manager.api;

import com.jobplatform.common.domain.Job;
import com.jobplatform.common.domain.JobStatus;
import com.jobplatform.common.domain.JobType;
import com.jobplatform.manager.api.dto.JobResponse;
import com.jobplatform.manager.service.IdempotencyConflictException;
import com.jobplatform.manager.service.InvalidJobRequestException;
import com.jobplatform.manager.service.JobNotFoundException;
import com.jobplatform.manager.service.JobService;
import com.jobplatform.manager.service.JobStateConflictException;
import com.jobplatform.manager.service.JobSubmissionService;
import com.jobplatform.manager.service.JobSubmissionService.SubmissionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class JobControllerTest {

    @Mock
    private JobSubmissionService submissionService;

    @Mock
    private JobService jobService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new JobController(submissionService, jobService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    private Job storedJob() {
        Job job = Job.create("word count", JobType.TEXT_PROCESSING, "hello", 5, null, LocalDateTime.of(2026, 3, 1, 10, 0),
                JobStatus.PENDING, 3, 60);
        ReflectionTestUtils.setField(job, "id", 11L);
        return job;
    }

    private static final String VALID_BODY = """
            {"name":"word count","jobType":"TEXT_PROCESSING","payload":"hello world","priority":5}
            """;

    @Test
    void creatingAJobReturns201WithTheJob() throws Exception {
        Job job = storedJob();
        when(submissionService.submit(any())).thenReturn(new SubmissionResult(job, true));
        when(jobService.get(11L)).thenReturn(JobResponse.detail(job, null));

        mockMvc.perform(post("/api/v1/jobs").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(11))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void duplicateSubmissionReturns200AndMarksTheReplay() throws Exception {
        Job job = storedJob();
        when(submissionService.submit(any())).thenReturn(new SubmissionResult(job, false));
        when(jobService.get(11L)).thenReturn(JobResponse.detail(job, "key-1"));

        mockMvc.perform(post("/api/v1/jobs").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotent-Replayed", "true"))
                .andExpect(jsonPath("$.idempotencyKey").value("key-1"));
    }

    @Test
    void emptyPayloadAndMissingFieldsAreRejectedWithFieldErrors() throws Exception {
        mockMvc.perform(post("/api/v1/jobs").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"payload\":\"\",\"priority\":99}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request validation failed"))
                .andExpect(jsonPath("$.fieldErrors[?(@.field=='payload')]").exists())
                .andExpect(jsonPath("$.fieldErrors[?(@.field=='jobType')]").exists())
                .andExpect(jsonPath("$.fieldErrors[?(@.field=='name')]").exists())
                .andExpect(jsonPath("$.fieldErrors[?(@.field=='priority')]").exists());
    }

    @Test
    void unknownJobTypeIsRejectedWithoutLeakingJavaExceptions() throws Exception {
        mockMvc.perform(post("/api/v1/jobs").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"x\",\"jobType\":\"NOT_A_TYPE\",\"payload\":\"p\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("jobType must be one of")))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Exception"))));
    }

    @Test
    void malformedJsonIsABadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/jobs").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void businessValidationFailuresAreBadRequests() throws Exception {
        when(submissionService.submit(any())).thenThrow(new InvalidJobRequestException("scheduledAt is in the past"));

        mockMvc.perform(post("/api/v1/jobs").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("scheduledAt is in the past"));
    }

    @Test
    void idempotencyKeyReusedWithADifferentRequestIsAConflict() throws Exception {
        when(submissionService.submit(any())).thenThrow(new IdempotencyConflictException("key-1"));

        mockMvc.perform(post("/api/v1/jobs").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isConflict());
    }

    @Test
    void unknownJobIsNotFound() throws Exception {
        when(jobService.get(99L)).thenThrow(new JobNotFoundException(99L));

        mockMvc.perform(get("/api/v1/jobs/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.path").value("/api/v1/jobs/99"));
    }

    @Test
    void cancellingARunningJobIsAConflict() throws Exception {
        when(jobService.cancel(11L)).thenThrow(new JobStateConflictException("Job 11 is running"));

        mockMvc.perform(post("/api/v1/jobs/11/cancel"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Job 11 is running"));
    }

    @Test
    void invalidStatusFilterListsTheAllowedValues() throws Exception {
        mockMvc.perform(get("/api/v1/jobs").param("status", "BOGUS"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("RETRYING")));
    }

    @Test
    void unexpectedFailuresReturnAGenericMessage() throws Exception {
        when(jobService.get(5L)).thenThrow(new IllegalStateException("database exploded at 10.0.0.5"));

        mockMvc.perform(get("/api/v1/jobs/5"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"));
    }
}

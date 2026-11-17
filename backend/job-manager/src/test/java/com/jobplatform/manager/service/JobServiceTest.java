package com.jobplatform.manager.service;

import com.jobplatform.common.domain.Job;
import com.jobplatform.common.domain.JobStatus;
import com.jobplatform.common.domain.JobType;
import com.jobplatform.common.repository.IdempotencyRecordRepository;
import com.jobplatform.common.repository.JobExecutionRepository;
import com.jobplatform.common.repository.JobRepository;
import com.jobplatform.common.service.JobStateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobServiceTest {

    @Mock
    private JobRepository jobRepository;

    @Mock
    private JobExecutionRepository executionRepository;

    @Mock
    private IdempotencyRecordRepository idempotencyRepository;

    @Mock
    private JobStateService jobStateService;

    private JobService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-03-01T10:00:00Z"), ZoneOffset.UTC);
        service = new JobService(jobRepository, executionRepository, idempotencyRepository, jobStateService, clock);
    }

    private Job job(JobStatus status) {
        return Job.create("job", JobType.TEXT_PROCESSING, "text", 5, null, LocalDateTime.of(2026, 3, 1, 10, 0),
                status, 3, 60);
    }

    @Test
    void cancelSucceedsWhenTheJobHasNotStartedRunning() {
        when(jobRepository.findById(1L)).thenReturn(Optional.of(job(JobStatus.PENDING)));
        when(jobStateService.cancel(anyLong(), any())).thenReturn(true);
        when(idempotencyRepository.findByJobId(1L)).thenReturn(Optional.empty());

        assertThat(service.cancel(1L)).isNotNull();
        verify(jobStateService).cancel(1L, LocalDateTime.of(2026, 3, 1, 10, 0));
    }

    @Test
    void cancellingARunningJobIsAConflictWithAnExplanation() {
        when(jobRepository.findById(1L)).thenReturn(Optional.of(job(JobStatus.RUNNING)));
        when(jobStateService.cancel(anyLong(), any())).thenReturn(false);

        assertThatThrownBy(() -> service.cancel(1L))
                .isInstanceOf(JobStateConflictException.class)
                .hasMessageContaining("running");
    }

    @Test
    void cancellingAFinishedJobIsAConflict() {
        when(jobRepository.findById(1L)).thenReturn(Optional.of(job(JobStatus.COMPLETED)));
        when(jobStateService.cancel(anyLong(), any())).thenReturn(false);

        assertThatThrownBy(() -> service.cancel(1L))
                .isInstanceOf(JobStateConflictException.class)
                .hasMessageContaining("COMPLETED");
    }

    @Test
    void cancellingAnUnknownJobIsNotFound() {
        when(jobRepository.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.cancel(9L)).isInstanceOf(JobNotFoundException.class);
    }

    @Test
    void listingFiltersByStatusAndTypeTogether() {
        when(jobRepository.findByStatusAndJobType(any(), any(), any(Pageable.class))).thenReturn(Page.empty());

        service.list(JobStatus.FAILED, JobType.DATA_PROCESSING, 0, 20, null);

        verify(jobRepository).findByStatusAndJobType(any(), any(), any(Pageable.class));
    }

    @Test
    void listingCapsThePageSizeAndAppliesTheRequestedSort() {
        when(jobRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);

        service.list(null, null, -3, 5000, "priority,asc");

        verify(jobRepository).findAll(pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isZero();
        assertThat(pageable.getValue().getPageSize()).isEqualTo(100);
        assertThat(pageable.getValue().getSort().getOrderFor("priority").getDirection()).isEqualTo(Sort.Direction.ASC);
    }

    @Test
    void listingRejectsUnknownSortFields() {
        assertThatThrownBy(() -> service.list(null, null, 0, 20, "payload,asc"))
                .isInstanceOf(InvalidJobRequestException.class);
    }
}

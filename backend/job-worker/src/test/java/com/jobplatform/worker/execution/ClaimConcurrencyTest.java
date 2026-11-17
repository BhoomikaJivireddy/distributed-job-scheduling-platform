package com.jobplatform.worker.execution;

import com.jobplatform.common.domain.Job;
import com.jobplatform.common.domain.JobExecution;
import com.jobplatform.common.domain.JobStatus;
import com.jobplatform.common.domain.JobType;
import com.jobplatform.common.repository.JobExecutionRepository;
import com.jobplatform.common.repository.JobRepository;
import com.jobplatform.common.service.AttemptRef;
import com.jobplatform.common.service.JobOutcome;
import com.jobplatform.common.service.JobOutcomeService;
import com.jobplatform.common.service.JobStateService;
import com.jobplatform.common.domain.ExecutionStatus;
import com.jobplatform.JobWorkerApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = JobWorkerApplication.class, properties = {
        "spring.rabbitmq.listener.simple.auto-startup=false",
        "spring.rabbitmq.password=unused",
        "platform.worker.id=it-worker",
        "platform.worker.heartbeat-interval-ms=3600000"
})
class ClaimConcurrencyTest {

    @Container
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.0");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private JobExecutionRepository executionRepository;

    @Autowired
    private JobStateService jobStateService;

    @Autowired
    private JobOutcomeService outcomeService;

    private Job queuedJob(int maxRetries) {
        LocalDateTime now = LocalDateTime.now();
        Job job = Job.create("it job", JobType.TEXT_PROCESSING, "payload", 5, null, now.minusSeconds(1),
                JobStatus.PENDING, maxRetries, 30);
        job.markQueued(now);
        return jobRepository.saveAndFlush(job);
    }

    @Test
    void whenManyWorkersClaimTheSameJobExactlyOneWins() throws Exception {
        Job job = queuedJob(3);
        int contenders = 12;
        ExecutorService pool = Executors.newFixedThreadPool(contenders);
        CountDownLatch startSignal = new CountDownLatch(1);

        List<Future<Optional<Job>>> futures = new ArrayList<>();
        for (int index = 0; index < contenders; index++) {
            String workerId = "contender-" + index;
            Callable<Optional<Job>> attempt = () -> {
                startSignal.await();
                LocalDateTime now = LocalDateTime.now();
                return jobStateService.claim(job.getId(), workerId, now, now.plusSeconds(60));
            };
            futures.add(pool.submit(attempt));
        }
        startSignal.countDown();

        int winners = 0;
        for (Future<Optional<Job>> future : futures) {
            if (future.get().isPresent()) {
                winners++;
            }
        }
        pool.shutdownNow();

        assertThat(winners).isEqualTo(1);
        Job stored = jobRepository.findById(job.getId()).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(JobStatus.RUNNING);
        assertThat(stored.getAttemptCount()).isEqualTo(1);
        List<JobExecution> executions = executionRepository.findByJobIdOrderByAttemptNumberAsc(job.getId());
        assertThat(executions).hasSize(1);
        assertThat(executions.get(0).getWorkerId()).isEqualTo(stored.getWorkerId());
    }

    @Test
    void aWorkerWhoseLeaseWasTakenOverCannotOverwriteTheNewerAttempt() {
        Job job = queuedJob(3);
        LocalDateTime now = LocalDateTime.now();
        jobStateService.claim(job.getId(), "slow-worker", now, now.plusSeconds(60)).orElseThrow();
        AttemptRef firstAttempt = new AttemptRef(job.getId(), "slow-worker", 1, 3);

        JobOutcome recovered = outcomeService.recordFailure(firstAttempt, ExecutionStatus.ABANDONED, "lease expired", true, 10);
        assertThat(recovered).isEqualTo(JobOutcome.RETRY_SCHEDULED);

        JobOutcome late = outcomeService.recordSuccess(firstAttempt, "late result", 20);

        assertThat(late).isEqualTo(JobOutcome.STALE);
        Job stored = jobRepository.findById(job.getId()).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(JobStatus.RETRYING);
        assertThat(executionRepository.findByJobIdOrderByAttemptNumberAsc(job.getId()))
                .extracting(JobExecution::getStatus).containsExactly(ExecutionStatus.ABANDONED);
    }

    @Test
    void cancelledQueuedJobCannotBeClaimed() {
        Job job = queuedJob(1);
        assertThat(jobStateService.cancel(job.getId(), LocalDateTime.now())).isTrue();

        LocalDateTime now = LocalDateTime.now();
        assertThat(jobStateService.claim(job.getId(), "worker", now, now.plusSeconds(60))).isEmpty();
        assertThat(jobRepository.findById(job.getId()).orElseThrow().getStatus()).isEqualTo(JobStatus.CANCELLED);
    }

    @Test
    void retriesAreExhaustedIntoDeadAfterMaxRetriesPlusOneAttempts() {
        Job job = queuedJob(1);
        LocalDateTime now = LocalDateTime.now();

        jobStateService.claim(job.getId(), "worker", now, now.plusSeconds(60)).orElseThrow();
        JobOutcome first = outcomeService.recordFailure(new AttemptRef(job.getId(), "worker", 1, 1),
                ExecutionStatus.FAILED, "boom", true, 5);
        assertThat(first).isEqualTo(JobOutcome.RETRY_SCHEDULED);

        Job retrying = jobRepository.findById(job.getId()).orElseThrow();
        retrying.markQueued(now);
        jobRepository.saveAndFlush(retrying);
        jobRepository.claim(job.getId(), JobStatus.QUEUED, JobStatus.RUNNING, "worker", now.plusDays(1), now.plusDays(2));
        JobOutcome second = outcomeService.recordFailure(new AttemptRef(job.getId(), "worker", 2, 1),
                ExecutionStatus.FAILED, "boom again", true, 5);

        assertThat(second).isEqualTo(JobOutcome.DEAD);
        assertThat(jobRepository.findById(job.getId()).orElseThrow().getStatus()).isEqualTo(JobStatus.DEAD);
    }
}

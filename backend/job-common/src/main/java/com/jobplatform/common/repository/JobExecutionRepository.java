package com.jobplatform.common.repository;

import com.jobplatform.common.domain.ExecutionStatus;
import com.jobplatform.common.domain.JobExecution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

public interface JobExecutionRepository extends JpaRepository<JobExecution, Long> {

    List<JobExecution> findByJobIdOrderByAttemptNumberAsc(Long jobId);

    long countByStartedAtAfter(LocalDateTime since);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update JobExecution e
            set e.status = :to, e.finishedAt = :now, e.durationMs = :durationMs, e.result = :result,
                e.errorMessage = :error, e.updatedAt = :now
            where e.jobId = :jobId and e.attemptNumber = :attempt and e.status = :from
            """)
    int finish(@Param("jobId") Long jobId,
               @Param("attempt") int attempt,
               @Param("from") ExecutionStatus from,
               @Param("to") ExecutionStatus to,
               @Param("now") LocalDateTime now,
               @Param("durationMs") long durationMs,
               @Param("result") String result,
               @Param("error") String error);

    @Query("select e.status, count(e) from JobExecution e group by e.status")
    List<Object[]> countGroupedByStatus();

    @Query("select avg(e.durationMs) from JobExecution e where e.status = :status")
    Double averageDurationMs(@Param("status") ExecutionStatus status);
}

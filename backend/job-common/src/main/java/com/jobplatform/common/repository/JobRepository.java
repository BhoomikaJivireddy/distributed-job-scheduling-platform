package com.jobplatform.common.repository;

import com.jobplatform.common.domain.Job;
import com.jobplatform.common.domain.JobStatus;
import com.jobplatform.common.domain.JobType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface JobRepository extends JpaRepository<Job, Long> {

    Page<Job> findByStatus(JobStatus status, Pageable pageable);

    Page<Job> findByJobType(JobType jobType, Pageable pageable);

    Page<Job> findByStatusAndJobType(JobStatus status, JobType jobType, Pageable pageable);

    long countByStatus(JobStatus status);

    @Query(value = """
            SELECT * FROM jobs
            WHERE (status IN ('PENDING', 'SCHEDULED', 'RETRYING') AND next_run_at <= :now)
               OR (status = 'QUEUED' AND queued_at <= :staleBefore)
            ORDER BY priority DESC, next_run_at ASC
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<Job> lockDispatchable(@Param("now") LocalDateTime now,
                               @Param("staleBefore") LocalDateTime staleBefore,
                               @Param("batchSize") int batchSize);

    @Query(value = """
            SELECT * FROM jobs
            WHERE status = 'RUNNING' AND lease_expires_at < :now
            ORDER BY lease_expires_at ASC
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<Job> lockExpiredLeases(@Param("now") LocalDateTime now, @Param("batchSize") int batchSize);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Job j
            set j.status = :to, j.workerId = :workerId, j.attemptCount = j.attemptCount + 1,
                j.startedAt = :now, j.finishedAt = null, j.leaseExpiresAt = :leaseExpiresAt, j.updatedAt = :now
            where j.id = :id and j.status = :from and j.nextRunAt <= :now
            """)
    int claim(@Param("id") Long id,
              @Param("from") JobStatus from,
              @Param("to") JobStatus to,
              @Param("workerId") String workerId,
              @Param("now") LocalDateTime now,
              @Param("leaseExpiresAt") LocalDateTime leaseExpiresAt);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Job j
            set j.status = :to, j.finishedAt = :finishedAt, j.nextRunAt = :nextRunAt, j.lastError = :error,
                j.leaseExpiresAt = null, j.updatedAt = :now
            where j.id = :id and j.status = :from and j.workerId = :workerId and j.attemptCount = :attempt
            """)
    int finishAttempt(@Param("id") Long id,
                      @Param("from") JobStatus from,
                      @Param("to") JobStatus to,
                      @Param("workerId") String workerId,
                      @Param("attempt") int attempt,
                      @Param("now") LocalDateTime now,
                      @Param("finishedAt") LocalDateTime finishedAt,
                      @Param("nextRunAt") LocalDateTime nextRunAt,
                      @Param("error") String error);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Job j
            set j.status = :to, j.finishedAt = :now, j.updatedAt = :now
            where j.id = :id and j.status in :from
            """)
    int cancel(@Param("id") Long id,
               @Param("from") Collection<JobStatus> from,
               @Param("to") JobStatus to,
               @Param("now") LocalDateTime now);

    @Query("select j.status, count(j) from Job j group by j.status")
    List<Object[]> countGroupedByStatus();

    @Query("select j.jobType, count(j) from Job j group by j.jobType")
    List<Object[]> countGroupedByType();

    @Query("select coalesce(sum(j.attemptCount - 1), 0) from Job j where j.attemptCount > 1")
    Long totalRetries();
}

package com.jobplatform.common.repository;

import com.jobplatform.common.domain.Worker;
import com.jobplatform.common.domain.WorkerStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

public interface WorkerRepository extends JpaRepository<Worker, String> {

    List<Worker> findAllByOrderByIdAsc();

    long countByStatusAndLastHeartbeatAtAfter(WorkerStatus status, LocalDateTime threshold);

    long countByStatus(WorkerStatus status);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            INSERT INTO workers (id, hostname, status, concurrency, current_executions, jobs_completed, jobs_failed,
                                 started_at, last_heartbeat_at, created_at, updated_at)
            VALUES (:id, :hostname, 'ACTIVE', :concurrency, 0, 0, 0, :now, :now, :now, :now) AS incoming
            ON DUPLICATE KEY UPDATE
                hostname = incoming.hostname,
                status = 'ACTIVE',
                concurrency = incoming.concurrency,
                current_executions = 0,
                started_at = incoming.started_at,
                last_heartbeat_at = incoming.last_heartbeat_at,
                updated_at = incoming.updated_at
            """, nativeQuery = true)
    int register(@Param("id") String id,
                 @Param("hostname") String hostname,
                 @Param("concurrency") int concurrency,
                 @Param("now") LocalDateTime now);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Worker w
            set w.status = :active, w.currentExecutions = :currentExecutions, w.lastHeartbeatAt = :now, w.updatedAt = :now
            where w.id = :id
            """)
    int heartbeat(@Param("id") String id,
                  @Param("active") WorkerStatus active,
                  @Param("currentExecutions") int currentExecutions,
                  @Param("now") LocalDateTime now);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Worker w set w.jobsCompleted = w.jobsCompleted + 1, w.updatedAt = :now where w.id = :id")
    int incrementCompleted(@Param("id") String id, @Param("now") LocalDateTime now);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Worker w set w.jobsFailed = w.jobsFailed + 1, w.updatedAt = :now where w.id = :id")
    int incrementFailed(@Param("id") String id, @Param("now") LocalDateTime now);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Worker w
            set w.status = :inactive, w.currentExecutions = 0, w.updatedAt = :now
            where w.id = :id
            """)
    int markInactive(@Param("id") String id,
                     @Param("inactive") WorkerStatus inactive,
                     @Param("now") LocalDateTime now);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Worker w
            set w.status = :inactive, w.currentExecutions = 0, w.updatedAt = :now
            where w.status <> :inactive and w.lastHeartbeatAt < :threshold
            """)
    int markStaleInactive(@Param("inactive") WorkerStatus inactive,
                          @Param("threshold") LocalDateTime threshold,
                          @Param("now") LocalDateTime now);
}

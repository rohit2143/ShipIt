package com.shipit.backend.job.repository;

import com.shipit.backend.job.entity.DeploymentJob;
import com.shipit.backend.job.entity.JobStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DeploymentJobRepository extends JpaRepository<DeploymentJob, UUID> {

    /**
     * Atomically selects and locks the next highest priority PENDING job using PostgreSQL's
     * FOR UPDATE SKIP LOCKED clause. Other concurrent workers will skip this row rather than waiting.
     */
    @Query(value = """
            SELECT * FROM deployment_jobs
            WHERE status = 'PENDING'
            ORDER BY priority DESC, queued_at ASC
            LIMIT 1
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    Optional<DeploymentJob> findNextPendingJobForUpdate();

    /**
     * Checks if an active job already exists for the given deployment ID.
     * Used for duplicate execution protection.
     */
    boolean existsByDeploymentIdAndStatusIn(UUID deploymentId, Collection<JobStatus> statuses);

    /**
     * Finds the most recent job for a deployment.
     */
    Optional<DeploymentJob> findTopByDeploymentIdOrderByCreatedAtDesc(UUID deploymentId);

    /**
     * Finds all jobs associated with a deployment.
     */
    List<DeploymentJob> findAllByDeploymentIdOrderByCreatedAtDesc(UUID deploymentId);

    /**
     * Finds running jobs whose heartbeat has expired past the given threshold.
     * Used by the stale worker recovery scanner.
     */
    List<DeploymentJob> findByStatusAndHeartbeatAtBefore(JobStatus status, Instant threshold);

    /**
     * Finds running jobs whose heartbeat is null and claimedAt is before the given threshold.
     */
    List<DeploymentJob> findByStatusAndHeartbeatAtIsNullAndClaimedAtBefore(JobStatus status, Instant threshold);

    /**
     * Finds all jobs by status.
     */
    List<DeploymentJob> findAllByStatus(JobStatus status);

    /**
     * Counts jobs by status.
     */
    long countByStatus(JobStatus status);

    /**
     * Finds a job by ID and verifies that the associated project belongs to the user.
     */
    @Query("""
            SELECT j FROM DeploymentJob j
            JOIN j.deployment d
            JOIN d.project p
            WHERE j.id = :jobId AND p.user.id = :userId
            """)
    Optional<DeploymentJob> findByIdAndUserId(@Param("jobId") UUID jobId, @Param("userId") UUID userId);

    /**
     * Finds all jobs for a deployment belonging to the user, ordered newest first.
     */
    @Query("""
            SELECT j FROM DeploymentJob j
            JOIN j.deployment d
            JOIN d.project p
            WHERE d.id = :deploymentId AND p.user.id = :userId
            ORDER BY j.createdAt DESC
            """)
    List<DeploymentJob> findAllByDeploymentIdAndUserIdOrderByCreatedAtDesc(
            @Param("deploymentId") UUID deploymentId,
            @Param("userId") UUID userId
    );
}

package com.shipit.backend.log.repository;

import com.shipit.backend.log.entity.DeploymentLog;
import com.shipit.backend.log.entity.DeploymentStage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for persisted deployment logs.
 */
@Repository
public interface DeploymentLogRepository extends JpaRepository<DeploymentLog, UUID> {

    /**
     * Retrieves all log entries for a deployment in ascending sequence order.
     */
    List<DeploymentLog> findByDeploymentIdOrderBySequenceNumberAsc(UUID deploymentId);

    /**
     * Retrieves paginated log entries for a deployment in ascending sequence order.
     */
    List<DeploymentLog> findByDeploymentIdOrderBySequenceNumberAsc(UUID deploymentId, Pageable pageable);

    /**
     * Retrieves paginated log entries filtered by execution stage in ascending sequence order.
     */
    List<DeploymentLog> findByDeploymentIdAndStageOrderBySequenceNumberAsc(
            UUID deploymentId,
            DeploymentStage stage,
            Pageable pageable
    );

    /**
     * Retrieves log entries that occurred after a specific sequence number (cursor-based pagination/polling).
     */
    List<DeploymentLog> findByDeploymentIdAndSequenceNumberGreaterThanOrderBySequenceNumberAsc(
            UUID deploymentId,
            long sequenceNumber,
            Pageable pageable
    );

    /**
     * Counts the total number of log lines recorded for a deployment.
     */
    long countByDeploymentId(UUID deploymentId);

    /**
     * Counts the total number of log lines recorded for a specific deployment stage.
     */
    long countByDeploymentIdAndStage(UUID deploymentId, DeploymentStage stage);

    /**
     * Finds the maximum sequence number allocated so far for a deployment.
     */
    @Query("SELECT COALESCE(MAX(l.sequenceNumber), 0L) FROM DeploymentLog l WHERE l.deployment.id = :deploymentId")
    long findMaxSequenceNumberByDeploymentId(@Param("deploymentId") UUID deploymentId);

    /**
     * Deletes all log entries associated with a specific deployment.
     */
    @Modifying
    @Query("DELETE FROM DeploymentLog l WHERE l.deployment.id = :deploymentId")
    void deleteByDeploymentId(@Param("deploymentId") UUID deploymentId);
}

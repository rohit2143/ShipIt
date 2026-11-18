package com.shipit.backend.deployment.repository;

import com.shipit.backend.deployment.entity.Deployment;
import com.shipit.backend.deployment.entity.DeploymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DeploymentRepository extends JpaRepository<Deployment, UUID> {

    Page<Deployment> findAllByProjectId(UUID projectId, Pageable pageable);

    Optional<Deployment> findByIdAndProjectId(UUID id, UUID projectId);

    @Query("SELECT d FROM Deployment d JOIN d.project p WHERE d.id = :id AND p.user.id = :userId")
    Optional<Deployment> findByIdAndUserId(@Param("id") UUID id, @Param("userId") UUID userId);

    boolean existsByProjectIdAndStatusIn(UUID projectId, Collection<DeploymentStatus> statuses);

    Optional<Deployment> findTopByProjectIdOrderByCreatedAtDesc(UUID projectId);

    Optional<Deployment> findTopByProjectIdAndStatusInOrderByCreatedAtDesc(
            UUID projectId, Collection<DeploymentStatus> statuses
    );

    long countByProjectId(UUID projectId);
}

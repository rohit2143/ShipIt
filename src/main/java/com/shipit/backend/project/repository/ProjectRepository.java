package com.shipit.backend.project.repository;

import com.shipit.backend.project.entity.Project;
import com.shipit.backend.project.entity.ProjectStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProjectRepository extends JpaRepository<Project, UUID> {

    Optional<Project> findByIdAndUserId(UUID id, UUID userId);

    List<Project> findAllByUserId(UUID userId);

    boolean existsByUserIdAndNormalizedName(UUID userId, String normalizedName);

    boolean existsByUserIdAndNormalizedNameAndIdNot(UUID userId, String normalizedName, UUID id);

    List<Project> findAllByRepositoryId(UUID repositoryId);

    @Query("""
        SELECT p FROM Project p
        WHERE p.repository.id = :repositoryId
          AND p.deploymentBranch = :deploymentBranch
          AND p.autoDeploy = true
          AND p.status = :status
    """)
    List<Project> findActiveAutoDeployProjects(
            @Param("repositoryId") UUID repositoryId,
            @Param("deploymentBranch") String deploymentBranch,
            @Param("status") ProjectStatus status
    );
}

package com.shipit.backend.project.repository;

import com.shipit.backend.project.entity.ProjectEnvironmentVariable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProjectEnvironmentVariableRepository extends JpaRepository<ProjectEnvironmentVariable, UUID> {

    List<ProjectEnvironmentVariable> findAllByProjectId(UUID projectId);

    Optional<ProjectEnvironmentVariable> findByProjectIdAndKey(UUID projectId, String key);

    boolean existsByProjectIdAndKey(UUID projectId, String key);

    void deleteAllByProjectId(UUID projectId);
}

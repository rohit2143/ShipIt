package com.shipit.backend.github.repository;

import com.shipit.backend.github.entity.GitHubConnection;
import com.shipit.backend.github.entity.GitHubConnectionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface GitHubConnectionRepository extends JpaRepository<GitHubConnection, UUID> {

    Optional<GitHubConnection> findByInstallationId(Long installationId);

    List<GitHubConnection> findAllByUserId(UUID userId);

    List<GitHubConnection> findAllByUserIdAndStatus(UUID userId, GitHubConnectionStatus status);

    Optional<GitHubConnection> findByIdAndUserId(UUID id, UUID userId);

    boolean existsByInstallationId(Long installationId);

    boolean existsByUserIdAndInstallationId(UUID userId, Long installationId);
}

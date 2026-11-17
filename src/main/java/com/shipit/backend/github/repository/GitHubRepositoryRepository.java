package com.shipit.backend.github.repository;

import com.shipit.backend.github.entity.GitHubRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface GitHubRepositoryRepository extends JpaRepository<GitHubRepository, UUID> {

    Optional<GitHubRepository> findByGithubRepoId(Long githubRepoId);

    Optional<GitHubRepository> findByIdAndUserId(UUID id, UUID userId);

    List<GitHubRepository> findAllByUserId(UUID userId);

    List<GitHubRepository> findAllByUserIdAndConnectionId(UUID userId, UUID connectionId);

    boolean existsByUserIdAndGithubRepoId(UUID userId, Long githubRepoId);

    boolean existsByUserIdAndFullName(UUID userId, String fullName);
}

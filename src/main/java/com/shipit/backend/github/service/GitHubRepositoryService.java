package com.shipit.backend.github.service;

import com.shipit.backend.common.exception.BadRequestException;
import com.shipit.backend.common.exception.ConflictException;
import com.shipit.backend.common.exception.ResourceNotFoundException;
import com.shipit.backend.github.client.GitHubAppClient;
import com.shipit.backend.github.dto.*;
import com.shipit.backend.github.entity.GitHubConnection;
import com.shipit.backend.github.entity.GitHubConnectionStatus;
import com.shipit.backend.github.entity.GitHubRepository;
import com.shipit.backend.github.repository.GitHubConnectionRepository;
import com.shipit.backend.github.repository.GitHubRepositoryRepository;
import com.shipit.backend.user.entity.User;
import com.shipit.backend.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class GitHubRepositoryService {

    private final GitHubRepositoryRepository repositoryRepository;
    private final GitHubConnectionRepository connectionRepository;
    private final UserService userService;
    private final GitHubAppClient appClient;
    private final GitHubInstallationTokenService installationTokenService;

    @Transactional(readOnly = true)
    public GitHubRepositoriesListResponse listInstallationRepositories(UUID userId, UUID connectionId, int page, int perPage) {
        GitHubConnection connection = getActiveConnection(userId, connectionId);

        String token = installationTokenService.getInstallationToken(connection.getInstallationId());
        GitHubInstallationRepositoriesDto dto = appClient.getInstallationRepositories(token, page, perPage);

        return new GitHubRepositoriesListResponse(dto.getTotalCount(), dto.getRepositories());
    }

    @Transactional(readOnly = true)
    public List<GitHubBranchResponse> listInstallationRepositoryBranches(
            UUID userId, UUID connectionId, String owner, String repo, int page, int perPage
    ) {
        GitHubConnection connection = getActiveConnection(userId, connectionId);

        String token = installationTokenService.getInstallationToken(connection.getInstallationId());
        List<GitHubBranchDto> branchDtos = appClient.getRepositoryBranches(token, owner, repo, page, perPage);

        return branchDtos.stream()
                .map(GitHubBranchResponse::fromDto)
                .toList();
    }

    @Transactional
    public GitHubRepositoryResponse connectRepository(UUID userId, ConnectRepositoryRequest request) {
        GitHubConnection connection = getActiveConnection(userId, request.getConnectionId());

        if (repositoryRepository.existsByUserIdAndGithubRepoId(userId, request.getGithubRepoId())) {
            throw new ConflictException("Repository is already connected to your account", "REPOSITORY_ALREADY_CONNECTED");
        }

        String token = installationTokenService.getInstallationToken(connection.getInstallationId());
        GitHubRepoDto repoDto = appClient.getRepositoryById(token, request.getGithubRepoId());

        User user = userService.getById(userId);

        GitHubRepository repo = GitHubRepository.builder()
                .user(user)
                .connection(connection)
                .githubRepoId(repoDto.getId())
                .name(repoDto.getName())
                .fullName(repoDto.getFullName())
                .ownerLogin(repoDto.getOwner() != null ? repoDto.getOwner().getLogin() : "")
                .ownerAvatarUrl(repoDto.getOwner() != null ? repoDto.getOwner().getAvatarUrl() : null)
                .htmlUrl(repoDto.getHtmlUrl())
                .cloneUrl(repoDto.getCloneUrl())
                .defaultBranch(repoDto.getDefaultBranch() != null ? repoDto.getDefaultBranch() : "main")
                .isPrivate(repoDto.isPrivate())
                .build();

        GitHubRepository saved = repositoryRepository.save(repo);
        log.info("User {} successfully connected repository {} (GitHub ID {})",
                userId, saved.getFullName(), saved.getGithubRepoId());

        return GitHubRepositoryResponse.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public List<GitHubRepositoryResponse> getUserRepositories(UUID userId) {
        return repositoryRepository.findAllByUserId(userId).stream()
                .map(GitHubRepositoryResponse::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public GitHubRepositoryResponse getUserRepository(UUID userId, UUID repositoryId) {
        GitHubRepository repo = repositoryRepository.findByIdAndUserId(repositoryId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Repository not found"));
        return GitHubRepositoryResponse.fromEntity(repo);
    }

    @Transactional(readOnly = true)
    public List<GitHubBranchResponse> listRepositoryBranches(UUID userId, UUID repositoryId, int page, int perPage) {
        GitHubRepository repo = repositoryRepository.findByIdAndUserId(repositoryId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Repository not found"));

        GitHubConnection connection = repo.getConnection();
        if (connection.getStatus() != GitHubConnectionStatus.CONNECTED) {
            throw new BadRequestException(
                    "Associated GitHub connection is not active. Current status: " + connection.getStatus(),
                    "CONNECTION_NOT_ACTIVE"
            );
        }

        String token = installationTokenService.getInstallationToken(connection.getInstallationId());
        List<GitHubBranchDto> branchDtos = appClient.getRepositoryBranches(
                token, repo.getOwnerLogin(), repo.getName(), page, perPage
        );

        return branchDtos.stream()
                .map(GitHubBranchResponse::fromDto)
                .toList();
    }

    @Transactional
    public void disconnectRepository(UUID userId, UUID repositoryId) {
        GitHubRepository repo = repositoryRepository.findByIdAndUserId(repositoryId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Repository not found"));

        repositoryRepository.delete(repo);
        log.info("User {} disconnected repository {}", userId, repo.getFullName());
    }

    private GitHubConnection getActiveConnection(UUID userId, UUID connectionId) {
        GitHubConnection connection = connectionRepository.findByIdAndUserId(connectionId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("GitHub connection not found"));

        if (connection.getStatus() != GitHubConnectionStatus.CONNECTED) {
            throw new BadRequestException(
                    "GitHub connection is not active. Current status: " + connection.getStatus(),
                    "CONNECTION_NOT_ACTIVE"
            );
        }

        return connection;
    }
}

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
import com.shipit.backend.user.entity.Role;
import com.shipit.backend.user.entity.User;
import com.shipit.backend.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GitHubRepositoryServiceTest {

    @Mock
    private GitHubRepositoryRepository repositoryRepository;

    @Mock
    private GitHubConnectionRepository connectionRepository;

    @Mock
    private UserService userService;

    @Mock
    private GitHubAppClient appClient;

    @Mock
    private GitHubInstallationTokenService installationTokenService;

    @InjectMocks
    private GitHubRepositoryService repositoryService;

    private User sampleUser;
    private UUID userId;
    private UUID connectionId;
    private GitHubConnection sampleConnection;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        connectionId = UUID.randomUUID();

        sampleUser = User.builder()
                .id(userId)
                .name("Developer")
                .email("dev@shipit.dev")
                .role(Role.ROLE_USER)
                .build();

        sampleConnection = GitHubConnection.builder()
                .id(connectionId)
                .user(sampleUser)
                .installationId(123456L)
                .status(GitHubConnectionStatus.CONNECTED)
                .connectedAt(Instant.now())
                .accountLogin("octocat-org")
                .build();
    }

    @Test
    @DisplayName("Should list installation repositories successfully when connection is CONNECTED")
    void shouldListInstallationRepositoriesSuccessfully() {
        when(connectionRepository.findByIdAndUserId(connectionId, userId)).thenReturn(Optional.of(sampleConnection));
        when(installationTokenService.getInstallationToken(123456L)).thenReturn("ghs_token");

        GitHubRepoDto repoDto = GitHubRepoDto.builder()
                .id(9988L)
                .name("demo-app")
                .fullName("octocat-org/demo-app")
                .isPrivate(false)
                .defaultBranch("main")
                .build();

        GitHubInstallationRepositoriesDto apiResponse = GitHubInstallationRepositoriesDto.builder()
                .totalCount(1)
                .repositories(List.of(repoDto))
                .build();

        when(appClient.getInstallationRepositories("ghs_token", 1, 30)).thenReturn(apiResponse);

        GitHubRepositoriesListResponse response = repositoryService.listInstallationRepositories(userId, connectionId, 1, 30);

        assertThat(response).isNotNull();
        assertThat(response.getTotalCount()).isEqualTo(1);
        assertThat(response.getRepositories()).hasSize(1);
        assertThat(response.getRepositories().get(0).getName()).isEqualTo("demo-app");
    }

    @Test
    @DisplayName("Should reject listing installation repositories when connection is PENDING_VERIFICATION")
    void shouldRejectListingReposWhenConnectionPending() {
        sampleConnection.setStatus(GitHubConnectionStatus.PENDING_VERIFICATION);
        when(connectionRepository.findByIdAndUserId(connectionId, userId)).thenReturn(Optional.of(sampleConnection));

        assertThatThrownBy(() -> repositoryService.listInstallationRepositories(userId, connectionId, 1, 30))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("not active")
                .extracting("errorCode")
                .isEqualTo("CONNECTION_NOT_ACTIVE");

        verify(installationTokenService, never()).getInstallationToken(any());
        verify(appClient, never()).getInstallationRepositories(any(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("Should reject listing installation repositories when connection does not exist or is unowned")
    void shouldThrowNotFoundWhenConnectionNotFound() {
        when(connectionRepository.findByIdAndUserId(connectionId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> repositoryService.listInstallationRepositories(userId, connectionId, 1, 30))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Should list installation branches for preview successfully")
    void shouldListInstallationBranchesSuccessfully() {
        when(connectionRepository.findByIdAndUserId(connectionId, userId)).thenReturn(Optional.of(sampleConnection));
        when(installationTokenService.getInstallationToken(123456L)).thenReturn("ghs_token");

        GitHubBranchDto branch = GitHubBranchDto.builder()
                .name("main")
                .commit(GitHubBranchCommitDto.builder().sha("abcdef123456").url("https://...").build())
                .isProtected(true)
                .build();

        when(appClient.getRepositoryBranches("ghs_token", "octocat-org", "demo-app", 1, 100))
                .thenReturn(List.of(branch));

        List<GitHubBranchResponse> branches = repositoryService.listInstallationRepositoryBranches(
                userId, connectionId, "octocat-org", "demo-app", 1, 100
        );

        assertThat(branches).hasSize(1);
        assertThat(branches.get(0).getName()).isEqualTo("main");
        assertThat(branches.get(0).getSha()).isEqualTo("abcdef123456");
        assertThat(branches.get(0).isProtected()).isTrue();
    }

    @Test
    @DisplayName("Should successfully connect a repository and persist its metadata")
    void shouldConnectRepositorySuccessfully() {
        ConnectRepositoryRequest request = ConnectRepositoryRequest.builder()
                .connectionId(connectionId)
                .githubRepoId(998877L)
                .build();

        when(connectionRepository.findByIdAndUserId(connectionId, userId)).thenReturn(Optional.of(sampleConnection));
        when(repositoryRepository.existsByUserIdAndGithubRepoId(userId, 998877L)).thenReturn(false);
        when(installationTokenService.getInstallationToken(123456L)).thenReturn("ghs_token");

        GitHubRepoDto repoDto = GitHubRepoDto.builder()
                .id(998877L)
                .name("cool-service")
                .fullName("octocat-org/cool-service")
                .owner(GitHubAccountDto.builder().login("octocat-org").avatarUrl("https://avatar.url").build())
                .htmlUrl("https://github.com/octocat-org/cool-service")
                .cloneUrl("https://github.com/octocat-org/cool-service.git")
                .defaultBranch("main")
                .isPrivate(true)
                .build();

        when(appClient.getRepositoryById("ghs_token", 998877L)).thenReturn(repoDto);
        when(userService.getById(userId)).thenReturn(sampleUser);
        when(repositoryRepository.save(any(GitHubRepository.class))).thenAnswer(inv -> {
            GitHubRepository repo = inv.getArgument(0);
            repo.setId(UUID.randomUUID());
            return repo;
        });

        GitHubRepositoryResponse response = repositoryService.connectRepository(userId, request);

        assertThat(response).isNotNull();
        assertThat(response.getGithubRepoId()).isEqualTo(998877L);
        assertThat(response.getFullName()).isEqualTo("octocat-org/cool-service");
        assertThat(response.isPrivate()).isTrue();
        assertThat(response.getDefaultBranch()).isEqualTo("main");
        verify(repositoryRepository).save(any(GitHubRepository.class));
    }

    @Test
    @DisplayName("Should reject connecting a repository that is already connected by the same user")
    void shouldRejectDuplicateRepositoryConnection() {
        ConnectRepositoryRequest request = ConnectRepositoryRequest.builder()
                .connectionId(connectionId)
                .githubRepoId(998877L)
                .build();

        when(connectionRepository.findByIdAndUserId(connectionId, userId)).thenReturn(Optional.of(sampleConnection));
        when(repositoryRepository.existsByUserIdAndGithubRepoId(userId, 998877L)).thenReturn(true);

        assertThatThrownBy(() -> repositoryService.connectRepository(userId, request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already connected")
                .extracting("errorCode")
                .isEqualTo("REPOSITORY_ALREADY_CONNECTED");

        verify(appClient, never()).getRepositoryById(any(), any());
        verify(repositoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should list branches of a connected repository")
    void shouldListBranchesOfConnectedRepository() {
        UUID repoId = UUID.randomUUID();
        GitHubRepository repo = GitHubRepository.builder()
                .id(repoId)
                .user(sampleUser)
                .connection(sampleConnection)
                .githubRepoId(1122L)
                .name("app-backend")
                .fullName("octocat-org/app-backend")
                .ownerLogin("octocat-org")
                .defaultBranch("main")
                .build();

        when(repositoryRepository.findByIdAndUserId(repoId, userId)).thenReturn(Optional.of(repo));
        when(installationTokenService.getInstallationToken(123456L)).thenReturn("ghs_token");

        GitHubBranchDto branch1 = GitHubBranchDto.builder().name("main").commit(new GitHubBranchCommitDto("sha1", "")).build();
        GitHubBranchDto branch2 = GitHubBranchDto.builder().name("staging").commit(new GitHubBranchCommitDto("sha2", "")).build();

        when(appClient.getRepositoryBranches("ghs_token", "octocat-org", "app-backend", 1, 100))
                .thenReturn(List.of(branch1, branch2));

        List<GitHubBranchResponse> branches = repositoryService.listRepositoryBranches(userId, repoId, 1, 100);

        assertThat(branches).hasSize(2);
        assertThat(branches.get(0).getName()).isEqualTo("main");
        assertThat(branches.get(1).getName()).isEqualTo("staging");
    }

    @Test
    @DisplayName("Should reject listing branches when repository's connection is DISCONNECTED")
    void shouldRejectListingBranchesWhenConnectionDisconnected() {
        UUID repoId = UUID.randomUUID();
        sampleConnection.setStatus(GitHubConnectionStatus.DISCONNECTED);

        GitHubRepository repo = GitHubRepository.builder()
                .id(repoId)
                .user(sampleUser)
                .connection(sampleConnection)
                .githubRepoId(1122L)
                .name("app-backend")
                .fullName("octocat-org/app-backend")
                .ownerLogin("octocat-org")
                .build();

        when(repositoryRepository.findByIdAndUserId(repoId, userId)).thenReturn(Optional.of(repo));

        assertThatThrownBy(() -> repositoryService.listRepositoryBranches(userId, repoId, 1, 100))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("not active")
                .extracting("errorCode")
                .isEqualTo("CONNECTION_NOT_ACTIVE");

        verify(appClient, never()).getRepositoryBranches(any(), any(), any(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("Should disconnect a repository successfully")
    void shouldDisconnectRepositorySuccessfully() {
        UUID repoId = UUID.randomUUID();
        GitHubRepository repo = GitHubRepository.builder()
                .id(repoId)
                .user(sampleUser)
                .connection(sampleConnection)
                .fullName("octocat-org/app-backend")
                .build();

        when(repositoryRepository.findByIdAndUserId(repoId, userId)).thenReturn(Optional.of(repo));

        repositoryService.disconnectRepository(userId, repoId);

        verify(repositoryRepository).delete(repo);
    }
}

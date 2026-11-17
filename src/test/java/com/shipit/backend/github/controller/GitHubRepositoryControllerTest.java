package com.shipit.backend.github.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shipit.backend.auth.security.UserPrincipal;
import com.shipit.backend.common.exception.ConflictException;
import com.shipit.backend.common.exception.GlobalExceptionHandler;
import com.shipit.backend.common.exception.ResourceNotFoundException;
import com.shipit.backend.github.dto.*;
import com.shipit.backend.github.service.GitHubRepositoryService;
import com.shipit.backend.user.entity.Role;
import com.shipit.backend.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class GitHubRepositoryControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private GitHubRepositoryService gitHubRepositoryService;

    @InjectMocks
    private GitHubRepositoryController gitHubRepositoryController;

    private User sampleUser;
    private UserPrincipal samplePrincipal;
    private UUID sampleUserId;

    @BeforeEach
    void setUp() {
        sampleUserId = UUID.randomUUID();
        sampleUser = User.builder()
                .id(sampleUserId)
                .name("Developer")
                .email("dev@shipit.dev")
                .role(Role.ROLE_USER)
                .build();

        samplePrincipal = UserPrincipal.create(sampleUser, "encoded-pwd");

        HandlerMethodArgumentResolver argumentResolver = new HandlerMethodArgumentResolver() {
            @Override
            public boolean supportsParameter(MethodParameter parameter) {
                return parameter.getParameterType().isAssignableFrom(UserPrincipal.class);
            }

            @Override
            public Object resolveArgument(MethodParameter parameter,
                                          ModelAndViewContainer mavContainer,
                                          NativeWebRequest webRequest,
                                          WebDataBinderFactory binderFactory) {
                return samplePrincipal;
            }
        };

        mockMvc = MockMvcBuilders.standaloneSetup(gitHubRepositoryController)
                .setCustomArgumentResolvers(argumentResolver)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("Should return 200 OK with installation repositories")
    void shouldListInstallationRepositories() throws Exception {
        UUID connectionId = UUID.randomUUID();
        GitHubRepoDto repoDto = GitHubRepoDto.builder()
                .id(1001L)
                .name("frontend")
                .fullName("octocat/frontend")
                .isPrivate(false)
                .defaultBranch("main")
                .build();

        GitHubRepositoriesListResponse response = new GitHubRepositoriesListResponse(1, List.of(repoDto));

        when(gitHubRepositoryService.listInstallationRepositories(sampleUserId, connectionId, 1, 30))
                .thenReturn(response);

        mockMvc.perform(get("/api/v1/github/connections/" + connectionId + "/repositories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalCount").value(1))
                .andExpect(jsonPath("$.data.repositories", hasSize(1)))
                .andExpect(jsonPath("$.data.repositories[0].name").value("frontend"));
    }

    @Test
    @DisplayName("Should return 200 OK with installation branches for preview")
    void shouldListInstallationBranches() throws Exception {
        UUID connectionId = UUID.randomUUID();
        GitHubBranchResponse branch = GitHubBranchResponse.builder()
                .name("main")
                .sha("abc123sha")
                .isProtected(true)
                .build();

        when(gitHubRepositoryService.listInstallationRepositoryBranches(sampleUserId, connectionId, "octocat", "frontend", 1, 100))
                .thenReturn(List.of(branch));

        mockMvc.perform(get("/api/v1/github/connections/" + connectionId + "/repositories/octocat/frontend/branches"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].name").value("main"))
                .andExpect(jsonPath("$.data[0].sha").value("abc123sha"));
    }

    @Test
    @DisplayName("Should return 201 Created on successfully connecting repository")
    void shouldConnectRepositorySuccessfully() throws Exception {
        UUID connectionId = UUID.randomUUID();
        ConnectRepositoryRequest request = ConnectRepositoryRequest.builder()
                .connectionId(connectionId)
                .githubRepoId(9988L)
                .build();

        GitHubRepositoryResponse response = GitHubRepositoryResponse.builder()
                .id(UUID.randomUUID())
                .connectionId(connectionId)
                .githubRepoId(9988L)
                .name("cool-api")
                .fullName("octocat/cool-api")
                .defaultBranch("main")
                .isPrivate(true)
                .createdAt(Instant.now())
                .build();

        when(gitHubRepositoryService.connectRepository(eq(sampleUserId), any(ConnectRepositoryRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/github/repositories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Repository connected successfully"))
                .andExpect(jsonPath("$.data.githubRepoId").value(9988))
                .andExpect(jsonPath("$.data.fullName").value("octocat/cool-api"));
    }

    @Test
    @DisplayName("Should return 400 Bad Request when connecting repository with missing fields")
    void shouldRejectConnectRepoWithMissingFields() throws Exception {
        ConnectRepositoryRequest invalidRequest = ConnectRepositoryRequest.builder()
                .connectionId(null)
                .githubRepoId(null)
                .build();

        mockMvc.perform(post("/api/v1/github/repositories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));

        verify(gitHubRepositoryService, never()).connectRepository(any(), any());
    }

    @Test
    @DisplayName("Should return 409 Conflict when repository is already connected")
    void shouldReturnConflictWhenAlreadyConnected() throws Exception {
        ConnectRepositoryRequest request = ConnectRepositoryRequest.builder()
                .connectionId(UUID.randomUUID())
                .githubRepoId(9988L)
                .build();

        when(gitHubRepositoryService.connectRepository(eq(sampleUserId), any(ConnectRepositoryRequest.class)))
                .thenThrow(new ConflictException("Repository is already connected to your account", "REPOSITORY_ALREADY_CONNECTED"));

        mockMvc.perform(post("/api/v1/github/repositories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("REPOSITORY_ALREADY_CONNECTED"));
    }

    @Test
    @DisplayName("Should return 200 OK with user's connected repositories")
    void shouldGetUserRepositories() throws Exception {
        GitHubRepositoryResponse repo = GitHubRepositoryResponse.builder()
                .id(UUID.randomUUID())
                .name("repo-1")
                .fullName("octocat/repo-1")
                .build();

        when(gitHubRepositoryService.getUserRepositories(sampleUserId)).thenReturn(List.of(repo));

        mockMvc.perform(get("/api/v1/github/repositories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].name").value("repo-1"));
    }

    @Test
    @DisplayName("Should return 200 OK with repository details by ID")
    void shouldGetRepositoryById() throws Exception {
        UUID repoId = UUID.randomUUID();
        GitHubRepositoryResponse repo = GitHubRepositoryResponse.builder()
                .id(repoId)
                .name("repo-1")
                .fullName("octocat/repo-1")
                .build();

        when(gitHubRepositoryService.getUserRepository(sampleUserId, repoId)).thenReturn(repo);

        mockMvc.perform(get("/api/v1/github/repositories/" + repoId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(repoId.toString()));
    }

    @Test
    @DisplayName("Should return 404 Not Found when repository does not exist")
    void shouldReturn404WhenRepoNotFound() throws Exception {
        UUID repoId = UUID.randomUUID();
        when(gitHubRepositoryService.getUserRepository(sampleUserId, repoId))
                .thenThrow(new ResourceNotFoundException("Repository not found"));

        mockMvc.perform(get("/api/v1/github/repositories/" + repoId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("Should return 200 OK with branches for connected repository")
    void shouldListBranchesForConnectedRepository() throws Exception {
        UUID repoId = UUID.randomUUID();
        GitHubBranchResponse branch = GitHubBranchResponse.builder()
                .name("dev")
                .sha("dev-sha-123")
                .isProtected(false)
                .build();

        when(gitHubRepositoryService.listRepositoryBranches(sampleUserId, repoId, 1, 100))
                .thenReturn(List.of(branch));

        mockMvc.perform(get("/api/v1/github/repositories/" + repoId + "/branches"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].name").value("dev"));
    }

    @Test
    @DisplayName("Should return 200 OK on disconnecting a repository")
    void shouldDisconnectRepository() throws Exception {
        UUID repoId = UUID.randomUUID();
        doNothing().when(gitHubRepositoryService).disconnectRepository(sampleUserId, repoId);

        mockMvc.perform(delete("/api/v1/github/repositories/" + repoId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Repository disconnected successfully"));

        verify(gitHubRepositoryService).disconnectRepository(sampleUserId, repoId);
    }
}

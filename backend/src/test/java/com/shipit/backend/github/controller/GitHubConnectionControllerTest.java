package com.shipit.backend.github.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shipit.backend.auth.security.UserPrincipal;
import com.shipit.backend.common.exception.BadRequestException;
import com.shipit.backend.common.exception.ConflictException;
import com.shipit.backend.common.exception.GlobalExceptionHandler;
import com.shipit.backend.github.dto.GitHubCallbackRequest;
import com.shipit.backend.github.dto.GitHubConnectionResponse;
import com.shipit.backend.github.dto.GitHubInstallUrlResponse;
import com.shipit.backend.github.entity.GitHubConnectionStatus;
import com.shipit.backend.github.service.GitHubConnectionService;
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

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class GitHubConnectionControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private GitHubConnectionService gitHubConnectionService;

    @InjectMocks
    private GitHubConnectionController gitHubConnectionController;

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

        mockMvc = MockMvcBuilders.standaloneSetup(gitHubConnectionController)
                .setCustomArgumentResolvers(argumentResolver)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("Should return 200 OK with install URL for current user")
    void shouldReturnInstallUrl() throws Exception {
        GitHubInstallUrlResponse response = GitHubInstallUrlResponse.builder()
                .installUrl("https://github.com/apps/shipit-app/installations/new?state=xyz")
                .build();

        when(gitHubConnectionService.createInstallationUrl(sampleUserId)).thenReturn(response);

        mockMvc.perform(get("/api/v1/github/connections/install-url"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.installUrl").value("https://github.com/apps/shipit-app/installations/new?state=xyz"));
    }

    @Test
    @DisplayName("Should return 200 OK on successful callback")
    void shouldHandleCallbackSuccessfully() throws Exception {
        GitHubCallbackRequest request = GitHubCallbackRequest.builder()
                .installationId(123456L)
                .setupAction("install")
                .state("valid-state")
                .build();

        GitHubConnectionResponse response = GitHubConnectionResponse.builder()
                .id(UUID.randomUUID())
                .installationId(123456L)
                .status(GitHubConnectionStatus.PENDING_VERIFICATION)
                .connectedAt(null)
                .build();

        when(gitHubConnectionService.handleInstallationCallback(eq(sampleUserId), any(GitHubCallbackRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/github/connections/callback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("GitHub App installation registered and pending verification"))
                .andExpect(jsonPath("$.data.installationId").value(123456))
                .andExpect(jsonPath("$.data.status").value("PENDING_VERIFICATION"))
                .andExpect(jsonPath("$.data.connectedAt").doesNotExist());
    }

    @Test
    @DisplayName("Should return 400 Bad Request with MISSING_SETUP_ACTION when setupAction is null")
    void shouldRejectCallbackWithNullSetupAction() throws Exception {
        GitHubCallbackRequest request = GitHubCallbackRequest.builder()
                .installationId(123456L)
                .setupAction(null)
                .state("state-token")
                .build();

        when(gitHubConnectionService.handleInstallationCallback(eq(sampleUserId), any(GitHubCallbackRequest.class)))
                .thenThrow(new BadRequestException("setup_action is required", "MISSING_SETUP_ACTION"));

        mockMvc.perform(post("/api/v1/github/connections/callback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("MISSING_SETUP_ACTION"));
    }

    @Test
    @DisplayName("Should return 400 Bad Request with MISSING_SETUP_ACTION when setupAction is blank")
    void shouldRejectCallbackWithBlankSetupAction() throws Exception {
        GitHubCallbackRequest request = GitHubCallbackRequest.builder()
                .installationId(123456L)
                .setupAction("   ")
                .state("state-token")
                .build();

        when(gitHubConnectionService.handleInstallationCallback(eq(sampleUserId), any(GitHubCallbackRequest.class)))
                .thenThrow(new BadRequestException("setup_action is required", "MISSING_SETUP_ACTION"));

        mockMvc.perform(post("/api/v1/github/connections/callback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("MISSING_SETUP_ACTION"));
    }

    @Test
    @DisplayName("Should return 400 Bad Request on invalid callback payload")
    void shouldValidateCallbackPayload() throws Exception {
        GitHubCallbackRequest invalidRequest = GitHubCallbackRequest.builder()
                .installationId(null) // missing
                .state("") // blank
                .build();

        mockMvc.perform(post("/api/v1/github/connections/callback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("Should return 409 Conflict when installation is already connected to another user")
    void shouldHandleConflictOnCallback() throws Exception {
        GitHubCallbackRequest request = GitHubCallbackRequest.builder()
                .installationId(99999L)
                .setupAction("install")
                .state("state-token")
                .build();

        when(gitHubConnectionService.handleInstallationCallback(eq(sampleUserId), any(GitHubCallbackRequest.class)))
                .thenThrow(new ConflictException("Installation already connected", "INSTALLATION_ALREADY_CONNECTED"));

        mockMvc.perform(post("/api/v1/github/connections/callback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("INSTALLATION_ALREADY_CONNECTED"));
    }

    @Test
    @DisplayName("Should return 200 OK with list of user connections")
    void shouldListUserConnections() throws Exception {
        GitHubConnectionResponse conn = GitHubConnectionResponse.builder()
                .id(UUID.randomUUID())
                .installationId(123456L)
                .status(GitHubConnectionStatus.PENDING_VERIFICATION)
                .connectedAt(Instant.now())
                .build();

        when(gitHubConnectionService.getUserConnections(sampleUserId)).thenReturn(List.of(conn));

        mockMvc.perform(get("/api/v1/github/connections"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].installationId").value(123456));
    }

    @Test
    @DisplayName("Should return 200 OK with connection details by ID")
    void shouldGetConnectionById() throws Exception {
        UUID connectionId = UUID.randomUUID();
        GitHubConnectionResponse conn = GitHubConnectionResponse.builder()
                .id(connectionId)
                .installationId(123456L)
                .status(GitHubConnectionStatus.PENDING_VERIFICATION)
                .connectedAt(Instant.now())
                .build();

        when(gitHubConnectionService.getConnection(sampleUserId, connectionId)).thenReturn(conn);

        mockMvc.perform(get("/api/v1/github/connections/" + connectionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(connectionId.toString()))
                .andExpect(jsonPath("$.data.installationId").value(123456));
    }

    @Test
    @DisplayName("Should return 200 OK on disconnecting an installation")
    void shouldDisconnectInstallation() throws Exception {
        UUID connectionId = UUID.randomUUID();
        doNothing().when(gitHubConnectionService).disconnectInstallation(sampleUserId, connectionId);

        mockMvc.perform(delete("/api/v1/github/connections/" + connectionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("GitHub App installation disconnected successfully"));

        verify(gitHubConnectionService).disconnectInstallation(sampleUserId, connectionId);
    }

    @Test
    @DisplayName("Should return 200 OK on successfully verifying a connection")
    void shouldVerifyConnectionSuccessfully() throws Exception {
        UUID connectionId = UUID.randomUUID();
        GitHubConnectionResponse verifiedResponse = GitHubConnectionResponse.builder()
                .id(connectionId)
                .installationId(123456L)
                .accountId(999L)
                .accountLogin("octocat-org")
                .accountType("Organization")
                .status(GitHubConnectionStatus.CONNECTED)
                .connectedAt(Instant.now())
                .build();

        when(gitHubConnectionService.verifyConnection(sampleUserId, connectionId)).thenReturn(verifiedResponse);

        mockMvc.perform(post("/api/v1/github/connections/" + connectionId + "/verify"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("GitHub App installation verified successfully"))
                .andExpect(jsonPath("$.data.status").value("CONNECTED"))
                .andExpect(jsonPath("$.data.accountLogin").value("octocat-org"));

        verify(gitHubConnectionService).verifyConnection(sampleUserId, connectionId);
    }

    @Test
    @DisplayName("Should return 404 Not Found when verifying non-existent connection")
    void shouldReturn404WhenVerifyingNonExistentConnection() throws Exception {
        UUID connectionId = UUID.randomUUID();
        when(gitHubConnectionService.verifyConnection(sampleUserId, connectionId))
                .thenThrow(new com.shipit.backend.common.exception.ResourceNotFoundException("GitHub connection not found"));

        mockMvc.perform(post("/api/v1/github/connections/" + connectionId + "/verify"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }
}

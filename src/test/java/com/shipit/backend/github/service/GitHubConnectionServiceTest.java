package com.shipit.backend.github.service;

import tools.jackson.databind.ObjectMapper;
import com.shipit.backend.common.exception.BadRequestException;
import com.shipit.backend.common.exception.ConflictException;
import com.shipit.backend.common.exception.ForbiddenException;
import com.shipit.backend.common.exception.ResourceNotFoundException;
import com.shipit.backend.common.util.HashUtil;
import com.shipit.backend.config.GitHubAppProperties;
import com.shipit.backend.github.client.GitHubAppClient;
import com.shipit.backend.github.dto.GitHubAccountDto;
import com.shipit.backend.github.dto.GitHubCallbackRequest;
import com.shipit.backend.github.dto.GitHubConnectionResponse;
import com.shipit.backend.github.dto.GitHubInstallUrlResponse;
import com.shipit.backend.github.dto.GitHubInstallationDto;
import com.shipit.backend.github.entity.GitHubConnection;
import com.shipit.backend.github.entity.GitHubConnectionStatus;
import com.shipit.backend.github.entity.GitHubInstallationState;
import com.shipit.backend.github.repository.GitHubConnectionRepository;
import com.shipit.backend.github.repository.GitHubInstallationStateRepository;
import com.shipit.backend.user.entity.Role;
import com.shipit.backend.user.entity.User;
import com.shipit.backend.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GitHubConnectionServiceTest {

    @Mock
    private GitHubAppProperties gitHubAppProperties;

    @Mock
    private GitHubInstallationStateRepository stateRepository;

    @Mock
    private GitHubConnectionRepository connectionRepository;

    @Mock
    private UserService userService;

    @Mock
    private GitHubAppClient gitHubAppClient;

    @Mock
    private GitHubInstallationTokenService installationTokenService;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private GitHubConnectionService gitHubConnectionService;

    private User sampleUser;
    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        sampleUser = User.builder()
                .id(userId)
                .name("ShipIt Developer")
                .email("dev@shipit.com")
                .role(Role.ROLE_USER)
                .build();
    }

    @Test
    @DisplayName("Should create installation URL with raw state and persist only SHA-256 state hash")
    void shouldCreateInstallationUrlWithHashedState() {
        when(userService.getById(userId)).thenReturn(sampleUser);
        when(gitHubAppProperties.getStateExpirationSeconds()).thenReturn(900L);
        when(gitHubAppProperties.getAppName()).thenReturn("shipit-app");

        GitHubInstallUrlResponse response = gitHubConnectionService.createInstallationUrl(userId);

        assertThat(response.getInstallUrl()).isNotBlank();
        assertThat(response.getInstallUrl()).startsWith("https://github.com/apps/shipit-app/installations/new?state=");

        String rawState = response.getInstallUrl().substring(response.getInstallUrl().indexOf("state=") + 6);
        assertThat(rawState).hasSize(64);

        ArgumentCaptor<GitHubInstallationState> stateCaptor = ArgumentCaptor.forClass(GitHubInstallationState.class);
        verify(stateRepository).save(stateCaptor.capture());

        GitHubInstallationState savedState = stateCaptor.getValue();
        assertThat(savedState.getUser()).isEqualTo(sampleUser);
        assertThat(savedState.getStateHash()).isEqualTo(HashUtil.sha256(rawState));
        assertThat(savedState.isUsed()).isFalse();
        assertThat(savedState.getExpiresAt()).isAfter(Instant.now());
    }

    @Test
    @DisplayName("Should successfully handle callback and create pending verification connection")
    void shouldSuccessfullyHandleCallbackAndCreatePendingConnection() {
        String rawState = "sample-raw-state-token";
        String stateHash = HashUtil.sha256(rawState);
        Long installationId = 987654L;

        GitHubCallbackRequest request = GitHubCallbackRequest.builder()
                .installationId(installationId)
                .setupAction("install")
                .state(rawState)
                .build();

        when(stateRepository.consumeStateAtomically(eq(stateHash), eq(userId), any(Instant.class))).thenReturn(1);
        when(connectionRepository.findByInstallationId(installationId)).thenReturn(Optional.empty());
        when(userService.getById(userId)).thenReturn(sampleUser);
        when(connectionRepository.save(any(GitHubConnection.class))).thenAnswer(inv -> {
            GitHubConnection conn = inv.getArgument(0);
            conn.setId(UUID.randomUUID());
            return conn;
        });

        GitHubConnectionResponse response = gitHubConnectionService.handleInstallationCallback(userId, request);

        assertThat(response).isNotNull();
        assertThat(response.getInstallationId()).isEqualTo(installationId);
        assertThat(response.getStatus()).isEqualTo(GitHubConnectionStatus.PENDING_VERIFICATION);
        assertThat(response.getConnectedAt()).isNull();

        ArgumentCaptor<GitHubConnection> connCaptor = ArgumentCaptor.forClass(GitHubConnection.class);
        verify(connectionRepository).save(connCaptor.capture());
        assertThat(connCaptor.getValue().getStatus()).isEqualTo(GitHubConnectionStatus.PENDING_VERIFICATION);
        assertThat(connCaptor.getValue().getConnectedAt()).isNull();
        assertThat(connCaptor.getValue().getAccountLogin()).isNull();
    }

    @Test
    @DisplayName("Should reject callback with null setupAction with MISSING_SETUP_ACTION without creating connection")
    void shouldRejectCallbackWithNullSetupAction() {
        GitHubCallbackRequest request = GitHubCallbackRequest.builder()
                .installationId(12345L)
                .setupAction(null)
                .state("any-state")
                .build();

        assertThatThrownBy(() -> gitHubConnectionService.handleInstallationCallback(userId, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("setup_action is required")
                .extracting("errorCode")
                .isEqualTo("MISSING_SETUP_ACTION");

        verify(stateRepository, never()).consumeStateAtomically(any(), any(), any());
        verify(connectionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should reject callback with blank setupAction with MISSING_SETUP_ACTION without creating connection")
    void shouldRejectCallbackWithBlankSetupAction() {
        GitHubCallbackRequest request = GitHubCallbackRequest.builder()
                .installationId(12345L)
                .setupAction("   ")
                .state("any-state")
                .build();

        assertThatThrownBy(() -> gitHubConnectionService.handleInstallationCallback(userId, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("setup_action is required")
                .extracting("errorCode")
                .isEqualTo("MISSING_SETUP_ACTION");

        verify(stateRepository, never()).consumeStateAtomically(any(), any(), any());
        verify(connectionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should reject callback with request setupAction without consuming state")
    void shouldRejectCallbackWithRequestSetupAction() {
        GitHubCallbackRequest request = GitHubCallbackRequest.builder()
                .installationId(12345L)
                .setupAction("request")
                .state("any-state")
                .build();

        assertThatThrownBy(() -> gitHubConnectionService.handleInstallationCallback(userId, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Installation request has been submitted")
                .extracting("errorCode")
                .isEqualTo("INSTALLATION_REQUEST_PENDING");

        verify(stateRepository, never()).consumeStateAtomically(any(), any(), any());
        verify(connectionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should reject callback with unsupported setupAction")
    void shouldRejectCallbackWithUnsupportedSetupAction() {
        GitHubCallbackRequest request = GitHubCallbackRequest.builder()
                .installationId(12345L)
                .setupAction("cancelled")
                .state("any-state")
                .build();

        assertThatThrownBy(() -> gitHubConnectionService.handleInstallationCallback(userId, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Unsupported setup action")
                .extracting("errorCode")
                .isEqualTo("UNSUPPORTED_SETUP_ACTION");

        verify(stateRepository, never()).consumeStateAtomically(any(), any(), any());
    }

    @Test
    @DisplayName("Should reactivate existing connection for same user in pending verification status")
    void shouldReactivateExistingConnectionForSameUser() {
        String rawState = "state-token-123";
        String stateHash = HashUtil.sha256(rawState);
        Long installationId = 55555L;

        GitHubCallbackRequest request = GitHubCallbackRequest.builder()
                .installationId(installationId)
                .setupAction("update")
                .state(rawState)
                .build();

        Instant previousConnectedAt = Instant.now().minusSeconds(1000);
        GitHubConnection existingConn = GitHubConnection.builder()
                .id(UUID.randomUUID())
                .user(sampleUser)
                .installationId(installationId)
                .status(GitHubConnectionStatus.DISCONNECTED)
                .connectedAt(previousConnectedAt)
                .build();

        when(stateRepository.consumeStateAtomically(eq(stateHash), eq(userId), any(Instant.class))).thenReturn(1);
        when(connectionRepository.findByInstallationId(installationId)).thenReturn(Optional.of(existingConn));
        when(connectionRepository.save(any(GitHubConnection.class))).thenAnswer(inv -> inv.getArgument(0));

        GitHubConnectionResponse response = gitHubConnectionService.handleInstallationCallback(userId, request);

        assertThat(response.getStatus()).isEqualTo(GitHubConnectionStatus.PENDING_VERIFICATION);
        assertThat(existingConn.getStatus()).isEqualTo(GitHubConnectionStatus.PENDING_VERIFICATION);
        assertThat(existingConn.getConnectedAt()).isEqualTo(previousConnectedAt);
    }

    @Test
    @DisplayName("Should reject installation if already connected to another ShipIt user")
    void shouldRejectInstallationConnectedToAnotherUser() {
        String rawState = "state-token-xyz";
        String stateHash = HashUtil.sha256(rawState);
        Long installationId = 77777L;

        User anotherUser = User.builder()
                .id(UUID.randomUUID())
                .email("other@shipit.com")
                .build();

        GitHubConnection existingConn = GitHubConnection.builder()
                .id(UUID.randomUUID())
                .user(anotherUser)
                .installationId(installationId)
                .status(GitHubConnectionStatus.CONNECTED)
                .connectedAt(Instant.now())
                .build();

        GitHubCallbackRequest request = GitHubCallbackRequest.builder()
                .installationId(installationId)
                .setupAction("install")
                .state(rawState)
                .build();

        when(stateRepository.consumeStateAtomically(eq(stateHash), eq(userId), any(Instant.class))).thenReturn(1);
        when(connectionRepository.findByInstallationId(installationId)).thenReturn(Optional.of(existingConn));

        assertThatThrownBy(() -> gitHubConnectionService.handleInstallationCallback(userId, request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already connected to another ShipIt user account");

        verify(connectionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should reject callback when state belongs to a different user without modifying it")
    void shouldRejectCallbackWhenStateBelongsToDifferentUser() {
        String rawState = "stolen-state";
        String stateHash = HashUtil.sha256(rawState);

        User legitimateOwner = User.builder()
                .id(UUID.randomUUID())
                .email("owner@shipit.com")
                .build();

        GitHubInstallationState state = GitHubInstallationState.builder()
                .user(legitimateOwner)
                .stateHash(stateHash)
                .expiresAt(Instant.now().plusSeconds(600))
                .used(false)
                .build();

        GitHubCallbackRequest request = GitHubCallbackRequest.builder()
                .installationId(12345L)
                .setupAction("install")
                .state(rawState)
                .build();

        // Atomic update returns 0 because s.user.id != userId
        when(stateRepository.consumeStateAtomically(eq(stateHash), eq(userId), any(Instant.class))).thenReturn(0);
        when(stateRepository.findByStateHash(stateHash)).thenReturn(Optional.of(state));

        assertThatThrownBy(() -> gitHubConnectionService.handleInstallationCallback(userId, request))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("Installation state does not belong to the authenticated user");

        assertThat(state.isUsed()).isFalse();
        verify(connectionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should reject callback when state is expired")
    void shouldRejectCallbackWhenStateIsExpired() {
        String rawState = "expired-state";
        String stateHash = HashUtil.sha256(rawState);

        GitHubInstallationState expiredState = GitHubInstallationState.builder()
                .user(sampleUser)
                .stateHash(stateHash)
                .expiresAt(Instant.now().minusSeconds(10))
                .used(false)
                .build();

        GitHubCallbackRequest request = GitHubCallbackRequest.builder()
                .installationId(12345L)
                .setupAction("install")
                .state(rawState)
                .build();

        when(stateRepository.consumeStateAtomically(eq(stateHash), eq(userId), any(Instant.class))).thenReturn(0);
        when(stateRepository.findByStateHash(stateHash)).thenReturn(Optional.of(expiredState));

        assertThatThrownBy(() -> gitHubConnectionService.handleInstallationCallback(userId, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("expired")
                .extracting("errorCode")
                .isEqualTo("STATE_EXPIRED");
    }

    @Test
    @DisplayName("Should reject callback when state has already been used")
    void shouldRejectCallbackWhenStateAlreadyUsed() {
        String rawState = "reused-state";
        String stateHash = HashUtil.sha256(rawState);

        GitHubInstallationState usedState = GitHubInstallationState.builder()
                .user(sampleUser)
                .stateHash(stateHash)
                .expiresAt(Instant.now().plusSeconds(600))
                .used(true)
                .build();

        GitHubCallbackRequest request = GitHubCallbackRequest.builder()
                .installationId(12345L)
                .setupAction("install")
                .state(rawState)
                .build();

        when(stateRepository.consumeStateAtomically(eq(stateHash), eq(userId), any(Instant.class))).thenReturn(0);
        when(stateRepository.findByStateHash(stateHash)).thenReturn(Optional.of(usedState));

        assertThatThrownBy(() -> gitHubConnectionService.handleInstallationCallback(userId, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("already been used")
                .extracting("errorCode")
                .isEqualTo("STATE_ALREADY_USED");
    }

    @Test
    @DisplayName("Should locally disconnect installation setting status to DISCONNECTED, not UNINSTALLED")
    void shouldLocallyDisconnectInstallation() {
        UUID connectionId = UUID.randomUUID();
        GitHubConnection connection = GitHubConnection.builder()
                .id(connectionId)
                .user(sampleUser)
                .installationId(123456L)
                .status(GitHubConnectionStatus.CONNECTED)
                .build();

        when(connectionRepository.findByIdAndUserId(connectionId, userId)).thenReturn(Optional.of(connection));

        gitHubConnectionService.disconnectInstallation(userId, connectionId);

        assertThat(connection.getStatus()).isEqualTo(GitHubConnectionStatus.DISCONNECTED);
        verify(installationTokenService).evictToken(123456L);
        verify(connectionRepository).save(connection);
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when disconnecting non-existent or unowned connection")
    void shouldThrowNotFoundWhenDisconnectingUnownedConnection() {
        UUID connectionId = UUID.randomUUID();
        when(connectionRepository.findByIdAndUserId(connectionId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> gitHubConnectionService.disconnectInstallation(userId, connectionId))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(connectionRepository, never()).save(any());
        verify(installationTokenService, never()).evictToken(any());
    }

    @Test
    @DisplayName("Should successfully verify pending connection, populate metadata, and set status to CONNECTED")
    void shouldSuccessfullyVerifyAndActivateConnection() throws Exception {
        UUID connectionId = UUID.randomUUID();
        Long installationId = 777888L;

        GitHubConnection connection = GitHubConnection.builder()
                .id(connectionId)
                .user(sampleUser)
                .installationId(installationId)
                .status(GitHubConnectionStatus.PENDING_VERIFICATION)
                .connectedAt(null)
                .build();

        GitHubInstallationDto installationDto = GitHubInstallationDto.builder()
                .id(installationId)
                .account(GitHubAccountDto.builder()
                        .id(999888L)
                        .login("shipit-org")
                        .type("Organization")
                        .avatarUrl("https://avatars.githubusercontent.com/u/999888")
                        .build())
                .repositorySelection("selected")
                .permissions(Map.of("contents", "read", "metadata", "read"))
                .suspendedAt(null)
                .build();

        when(connectionRepository.findByIdAndUserId(connectionId, userId)).thenReturn(Optional.of(connection));
        when(gitHubAppClient.getInstallation(installationId)).thenReturn(installationDto);
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"contents\":\"read\",\"metadata\":\"read\"}");
        when(connectionRepository.save(any(GitHubConnection.class))).thenAnswer(inv -> inv.getArgument(0));

        GitHubConnectionResponse response = gitHubConnectionService.verifyConnection(userId, connectionId);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(GitHubConnectionStatus.CONNECTED);
        assertThat(response.getConnectedAt()).isNotNull();
        assertThat(response.getAccountId()).isEqualTo(999888L);
        assertThat(response.getAccountLogin()).isEqualTo("shipit-org");
        assertThat(response.getAccountType()).isEqualTo("Organization");
        assertThat(response.getAccountAvatarUrl()).isEqualTo("https://avatars.githubusercontent.com/u/999888");
        assertThat(response.getRepositorySelection()).isEqualTo("selected");

        verify(connectionRepository).save(connection);
        assertThat(connection.getStatus()).isEqualTo(GitHubConnectionStatus.CONNECTED);
        assertThat(connection.getConnectedAt()).isNotNull();
        assertThat(connection.getPermissions()).isEqualTo("{\"contents\":\"read\",\"metadata\":\"read\"}");
    }

    @Test
    @DisplayName("Should mark connection SUSPENDED and evict token when installation is suspended on GitHub")
    void shouldSetSuspendedStatusWhenSuspendedOnGitHub() {
        UUID connectionId = UUID.randomUUID();
        Long installationId = 777888L;

        GitHubConnection connection = GitHubConnection.builder()
                .id(connectionId)
                .user(sampleUser)
                .installationId(installationId)
                .status(GitHubConnectionStatus.PENDING_VERIFICATION)
                .connectedAt(null)
                .build();

        GitHubInstallationDto installationDto = GitHubInstallationDto.builder()
                .id(installationId)
                .account(GitHubAccountDto.builder().id(999888L).login("shipit-org").type("Organization").build())
                .repositorySelection("all")
                .suspendedAt(Instant.now())
                .build();

        when(connectionRepository.findByIdAndUserId(connectionId, userId)).thenReturn(Optional.of(connection));
        when(gitHubAppClient.getInstallation(installationId)).thenReturn(installationDto);
        when(connectionRepository.save(any(GitHubConnection.class))).thenAnswer(inv -> inv.getArgument(0));

        GitHubConnectionResponse response = gitHubConnectionService.verifyConnection(userId, connectionId);

        assertThat(response.getStatus()).isEqualTo(GitHubConnectionStatus.SUSPENDED);
        assertThat(connection.getStatus()).isEqualTo(GitHubConnectionStatus.SUSPENDED);
        verify(installationTokenService).evictToken(installationId);
        verify(connectionRepository).save(connection);
    }

    @Test
    @DisplayName("Should preserve existing connectedAt when reverifying already connected installation")
    void shouldPreserveExistingConnectedAtWhenReverifying() {
        UUID connectionId = UUID.randomUUID();
        Long installationId = 777888L;
        Instant initialConnectedAt = Instant.now().minusSeconds(3600);

        GitHubConnection connection = GitHubConnection.builder()
                .id(connectionId)
                .user(sampleUser)
                .installationId(installationId)
                .status(GitHubConnectionStatus.CONNECTED)
                .connectedAt(initialConnectedAt)
                .build();

        GitHubInstallationDto installationDto = GitHubInstallationDto.builder()
                .id(installationId)
                .account(GitHubAccountDto.builder().id(999888L).login("shipit-org").type("Organization").build())
                .repositorySelection("all")
                .suspendedAt(null)
                .build();

        when(connectionRepository.findByIdAndUserId(connectionId, userId)).thenReturn(Optional.of(connection));
        when(gitHubAppClient.getInstallation(installationId)).thenReturn(installationDto);
        when(connectionRepository.save(any(GitHubConnection.class))).thenAnswer(inv -> inv.getArgument(0));

        GitHubConnectionResponse response = gitHubConnectionService.verifyConnection(userId, connectionId);

        assertThat(response.getConnectedAt()).isEqualTo(initialConnectedAt);
        assertThat(connection.getConnectedAt()).isEqualTo(initialConnectedAt);
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when verifying unowned or non-existent connection")
    void shouldThrowNotFoundWhenVerifyingUnownedConnection() {
        UUID connectionId = UUID.randomUUID();
        when(connectionRepository.findByIdAndUserId(connectionId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> gitHubConnectionService.verifyConnection(userId, connectionId))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(gitHubAppClient, never()).getInstallation(any());
        verify(connectionRepository, never()).save(any());
    }
}

package com.shipit.backend.github.service;

import tools.jackson.databind.ObjectMapper;
import com.shipit.backend.common.exception.BadRequestException;
import com.shipit.backend.common.exception.ConflictException;
import com.shipit.backend.common.exception.ForbiddenException;
import com.shipit.backend.common.exception.ResourceNotFoundException;
import com.shipit.backend.common.util.HashUtil;
import com.shipit.backend.config.GitHubAppProperties;
import com.shipit.backend.github.client.GitHubAppClient;
import com.shipit.backend.github.dto.GitHubCallbackRequest;
import com.shipit.backend.github.dto.GitHubConnectionResponse;
import com.shipit.backend.github.dto.GitHubInstallUrlResponse;
import com.shipit.backend.github.dto.GitHubInstallationDto;
import com.shipit.backend.github.entity.GitHubConnection;
import com.shipit.backend.github.entity.GitHubConnectionStatus;
import com.shipit.backend.github.entity.GitHubInstallationState;
import com.shipit.backend.github.repository.GitHubConnectionRepository;
import com.shipit.backend.github.repository.GitHubInstallationStateRepository;
import com.shipit.backend.user.entity.User;
import com.shipit.backend.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class GitHubConnectionService {

    private final GitHubAppProperties gitHubAppProperties;
    private final GitHubInstallationStateRepository stateRepository;
    private final GitHubConnectionRepository connectionRepository;
    private final UserService userService;
    private final GitHubAppClient gitHubAppClient;
    private final GitHubInstallationTokenService installationTokenService;
    private final ObjectMapper objectMapper;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Transactional
    public GitHubInstallUrlResponse createInstallationUrl(UUID userId) {
        User user = userService.getById(userId);

        byte[] randomBytes = new byte[32];
        SECURE_RANDOM.nextBytes(randomBytes);
        String rawState = HexFormat.of().formatHex(randomBytes);

        String stateHash = HashUtil.sha256(rawState);
        Instant expiresAt = Instant.now().plusSeconds(gitHubAppProperties.getStateExpirationSeconds());

        GitHubInstallationState state = GitHubInstallationState.builder()
                .user(user)
                .stateHash(stateHash)
                .expiresAt(expiresAt)
                .used(false)
                .build();

        stateRepository.save(state);
        log.info("Generated GitHub installation state for user {}", userId);

        String appName = gitHubAppProperties.getAppName();
        String installUrl = String.format("https://github.com/apps/%s/installations/new?state=%s", appName, rawState);

        return GitHubInstallUrlResponse.builder()
                .installUrl(installUrl)
                .build();
    }

    @Transactional
    public GitHubConnectionResponse handleInstallationCallback(UUID userId, GitHubCallbackRequest request) {
        String setupAction = request.getSetupAction();
        if (setupAction == null || setupAction.isBlank()) {
            throw new BadRequestException("setup_action is required", "MISSING_SETUP_ACTION");
        }

        if ("request".equalsIgnoreCase(setupAction)) {
            throw new BadRequestException(
                    "Installation request has been submitted to your organization administrators for approval.",
                    "INSTALLATION_REQUEST_PENDING"
            );
        }

        if (!"install".equalsIgnoreCase(setupAction) && !"update".equalsIgnoreCase(setupAction)) {
            throw new BadRequestException(
                    "Unsupported setup action: " + setupAction,
                    "UNSUPPORTED_SETUP_ACTION"
            );
        }

        String stateHash = HashUtil.sha256(request.getState());

        // Concurrency-safe atomic state consumption bound to authenticated userId
        int updated = stateRepository.consumeStateAtomically(stateHash, userId, Instant.now());
        if (updated == 0) {
            handleFailedStateConsumption(stateHash, userId);
        }

        log.info("Atomically consumed installation state for user {}", userId);

        // Check if installation is already registered
        Optional<GitHubConnection> existingConnection =
                connectionRepository.findByInstallationId(request.getInstallationId());

        GitHubConnection connection;
        if (existingConnection.isPresent()) {
            connection = existingConnection.get();
            if (!connection.getUser().getId().equals(userId)) {
                log.warn("User {} attempted to link installation {} owned by user {}",
                        userId, request.getInstallationId(), connection.getUser().getId());
                throw new ConflictException(
                        "This GitHub App installation is already connected to another ShipIt user account.",
                        "INSTALLATION_ALREADY_CONNECTED"
                );
            }

            connection.setStatus(GitHubConnectionStatus.PENDING_VERIFICATION);
            log.info("Reactivated GitHub connection {} for user {}", connection.getId(), userId);
        } else {
            User user = userService.getById(userId);
            connection = GitHubConnection.builder()
                    .user(user)
                    .installationId(request.getInstallationId())
                    .status(GitHubConnectionStatus.PENDING_VERIFICATION)
                    .connectedAt(null)
                    .build();
            log.info("Registered new GitHub installation {} for user {} pending verification",
                    request.getInstallationId(), userId);
        }

        GitHubConnection saved = connectionRepository.save(connection);
        return GitHubConnectionResponse.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public List<GitHubConnectionResponse> getUserConnections(UUID userId) {
        return connectionRepository.findAllByUserId(userId).stream()
                .filter(conn -> conn.getStatus() != GitHubConnectionStatus.UNINSTALLED)
                .map(GitHubConnectionResponse::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public GitHubConnectionResponse getConnection(UUID userId, UUID connectionId) {
        GitHubConnection connection = connectionRepository.findByIdAndUserId(connectionId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("GitHub connection not found"));
        return GitHubConnectionResponse.fromEntity(connection);
    }

    @Transactional
    public GitHubConnectionResponse verifyConnection(UUID userId, UUID connectionId) {
        GitHubConnection connection = connectionRepository.findByIdAndUserId(connectionId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("GitHub connection not found"));

        GitHubInstallationDto installation = gitHubAppClient.getInstallation(connection.getInstallationId());

        if (installation.getAccount() != null) {
            connection.setAccountId(installation.getAccount().getId());
            connection.setAccountLogin(installation.getAccount().getLogin());
            connection.setAccountType(installation.getAccount().getType());
            connection.setAccountAvatarUrl(installation.getAccount().getAvatarUrl());
        }

        connection.setRepositorySelection(installation.getRepositorySelection());

        if (installation.getPermissions() != null) {
            try {
                connection.setPermissions(objectMapper.writeValueAsString(installation.getPermissions()));
            } catch (Exception e) {
                log.warn("Failed to serialize installation permissions to JSON: {}", e.getMessage());
            }
        }

        if (installation.getSuspendedAt() != null) {
            connection.setStatus(GitHubConnectionStatus.SUSPENDED);
            installationTokenService.evictToken(connection.getInstallationId());
            log.info("GitHub installation {} is suspended on GitHub; marked SUSPENDED", connection.getInstallationId());
        } else {
            connection.setStatus(GitHubConnectionStatus.CONNECTED);
            if (connection.getConnectedAt() == null) {
                connection.setConnectedAt(Instant.now());
            }
            log.info("GitHub installation {} verified and marked CONNECTED for user {}",
                    connection.getInstallationId(), userId);
        }

        GitHubConnection saved = connectionRepository.save(connection);
        return GitHubConnectionResponse.fromEntity(saved);
    }

    @Transactional
    public void disconnectInstallation(UUID userId, UUID connectionId) {
        GitHubConnection connection = connectionRepository.findByIdAndUserId(connectionId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("GitHub connection not found"));

        connection.setStatus(GitHubConnectionStatus.DISCONNECTED);
        installationTokenService.evictToken(connection.getInstallationId());
        connectionRepository.save(connection);
        log.info("User {} disconnected GitHub installation {}", userId, connection.getInstallationId());
    }

    private void handleFailedStateConsumption(String stateHash, UUID userId) {
        Optional<GitHubInstallationState> existingState = stateRepository.findByStateHash(stateHash);
        if (existingState.isEmpty()) {
            throw new BadRequestException("Invalid installation state", "INVALID_STATE");
        }

        GitHubInstallationState state = existingState.get();
        if (!state.getUser().getId().equals(userId)) {
            log.warn("User {} attempted to consume state belonging to user {}", userId, state.getUser().getId());
            throw new ForbiddenException("Installation state does not belong to the authenticated user");
        }

        if (state.isUsed()) {
            throw new BadRequestException("Installation state has already been used", "STATE_ALREADY_USED");
        }

        if (state.getExpiresAt().isBefore(Instant.now())) {
            throw new BadRequestException("Installation state has expired", "STATE_EXPIRED");
        }

        throw new BadRequestException("Failed to validate installation state", "STATE_VALIDATION_FAILED");
    }
}

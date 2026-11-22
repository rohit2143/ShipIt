package com.shipit.backend.github.repository;

import com.shipit.backend.github.entity.GitHubConnection;
import com.shipit.backend.github.entity.GitHubConnectionStatus;
import com.shipit.backend.github.entity.GitHubInstallationState;
import com.shipit.backend.user.entity.Role;
import com.shipit.backend.user.entity.User;
import com.shipit.backend.user.entity.UserStatus;
import com.shipit.backend.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class GitHubConnectionRepositoryTest {

    @Autowired
    private GitHubConnectionRepository connectionRepository;

    @Autowired
    private GitHubInstallationStateRepository stateRepository;

    @Autowired
    private UserRepository userRepository;

    private User sampleUser;

    @BeforeEach
    void setUp() {
        sampleUser = userRepository.save(User.builder()
                .name("GitHub Tester")
                .email("github-tester-" + UUID.randomUUID() + "@shipit.dev")
                .role(Role.ROLE_USER)
                .status(UserStatus.ACTIVE)
                .emailVerified(true)
                .build());
    }

    @Test
    @DisplayName("Should persist and retrieve GitHubConnection with null connectedAt in pending verification")
    void shouldPersistAndRetrieveGitHubConnection() {
        Long installationId = 987654321L;

        GitHubConnection connection = GitHubConnection.builder()
                .user(sampleUser)
                .installationId(installationId)
                .status(GitHubConnectionStatus.PENDING_VERIFICATION)
                .connectedAt(null)
                .build();

        GitHubConnection saved = connectionRepository.save(connection);
        assertThat(saved.getId()).isNotNull();

        Optional<GitHubConnection> byInstallId = connectionRepository.findByInstallationId(installationId);
        assertThat(byInstallId).isPresent();
        assertThat(byInstallId.get().getUser().getId()).isEqualTo(sampleUser.getId());
        assertThat(byInstallId.get().getStatus()).isEqualTo(GitHubConnectionStatus.PENDING_VERIFICATION);
        assertThat(byInstallId.get().getConnectedAt()).isNull();

        List<GitHubConnection> userConns = connectionRepository.findAllByUserId(sampleUser.getId());
        assertThat(userConns).hasSize(1);
    }

    @Test
    @DisplayName("Should enforce unique constraint on installationId across users")
    void shouldEnforceUniqueConstraintOnInstallationId() {
        Long duplicateInstallationId = 11223344L;

        connectionRepository.saveAndFlush(GitHubConnection.builder()
                .user(sampleUser)
                .installationId(duplicateInstallationId)
                .status(GitHubConnectionStatus.PENDING_VERIFICATION)
                .connectedAt(Instant.now())
                .build());

        User anotherUser = userRepository.save(User.builder()
                .name("Another User")
                .email("another-" + UUID.randomUUID() + "@shipit.dev")
                .role(Role.ROLE_USER)
                .status(UserStatus.ACTIVE)
                .emailVerified(true)
                .build());

        assertThatThrownBy(() -> connectionRepository.saveAndFlush(GitHubConnection.builder()
                .user(anotherUser)
                .installationId(duplicateInstallationId)
                .status(GitHubConnectionStatus.PENDING_VERIFICATION)
                .connectedAt(Instant.now())
                .build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Should atomically consume state for matching user and reject subsequent or unauthorized attempts")
    void shouldAtomicallyConsumeState() {
        String stateHash = "sample-sha256-hash-" + UUID.randomUUID();

        GitHubInstallationState state = stateRepository.saveAndFlush(GitHubInstallationState.builder()
                .user(sampleUser)
                .stateHash(stateHash)
                .expiresAt(Instant.now().plusSeconds(600))
                .used(false)
                .build());

        // 1. Attempt consumption with a different user ID -> should return 0 (ownership protection)
        UUID foreignUserId = UUID.randomUUID();
        int foreignAttempt = stateRepository.consumeStateAtomically(stateHash, foreignUserId, Instant.now());
        assertThat(foreignAttempt).isEqualTo(0);

        // Verify state is still intact
        GitHubInstallationState afterForeignAttempt = stateRepository.findByStateHash(stateHash).orElseThrow();
        assertThat(afterForeignAttempt.isUsed()).isFalse();

        // 2. Legitimate user consumes state -> should return 1
        int legitimateAttempt = stateRepository.consumeStateAtomically(stateHash, sampleUser.getId(), Instant.now());
        assertThat(legitimateAttempt).isEqualTo(1);

        // Verify state is now marked used
        GitHubInstallationState afterLegitAttempt = stateRepository.findByStateHash(stateHash).orElseThrow();
        assertThat(afterLegitAttempt.isUsed()).isTrue();

        // 3. Replay attempt by legitimate user -> should return 0 (already used)
        int replayAttempt = stateRepository.consumeStateAtomically(stateHash, sampleUser.getId(), Instant.now());
        assertThat(replayAttempt).isEqualTo(0);
    }
}

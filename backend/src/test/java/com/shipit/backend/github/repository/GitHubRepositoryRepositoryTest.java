package com.shipit.backend.github.repository;

import com.shipit.backend.github.entity.GitHubConnection;
import com.shipit.backend.github.entity.GitHubConnectionStatus;
import com.shipit.backend.github.entity.GitHubRepository;
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
class GitHubRepositoryRepositoryTest {

    @Autowired
    private GitHubRepositoryRepository repositoryRepository;

    @Autowired
    private GitHubConnectionRepository connectionRepository;

    @Autowired
    private UserRepository userRepository;

    private User sampleUser;
    private GitHubConnection sampleConnection;

    @BeforeEach
    void setUp() {
        sampleUser = userRepository.save(User.builder()
                .name("Repo Tester")
                .email("repo-tester-" + UUID.randomUUID() + "@shipit.dev")
                .role(Role.ROLE_USER)
                .status(UserStatus.ACTIVE)
                .emailVerified(true)
                .build());

        sampleConnection = connectionRepository.save(GitHubConnection.builder()
                .user(sampleUser)
                .installationId(System.currentTimeMillis())
                .status(GitHubConnectionStatus.CONNECTED)
                .connectedAt(Instant.now())
                .accountLogin("tester-org")
                .build());
    }

    @Test
    @DisplayName("Should persist and retrieve GitHubRepository with all metadata")
    void shouldPersistAndRetrieveGitHubRepository() {
        Long githubRepoId = 123456789L;

        GitHubRepository repo = GitHubRepository.builder()
                .user(sampleUser)
                .connection(sampleConnection)
                .githubRepoId(githubRepoId)
                .name("shipit-backend")
                .fullName("tester-org/shipit-backend")
                .ownerLogin("tester-org")
                .ownerAvatarUrl("https://avatars.githubusercontent.com/u/12345")
                .htmlUrl("https://github.com/tester-org/shipit-backend")
                .cloneUrl("https://github.com/tester-org/shipit-backend.git")
                .defaultBranch("main")
                .isPrivate(true)
                .build();

        GitHubRepository saved = repositoryRepository.save(repo);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();

        Optional<GitHubRepository> found = repositoryRepository.findByIdAndUserId(saved.getId(), sampleUser.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("shipit-backend");
        assertThat(found.get().getFullName()).isEqualTo("tester-org/shipit-backend");
        assertThat(found.get().isPrivate()).isTrue();
        assertThat(found.get().getDefaultBranch()).isEqualTo("main");
    }

    @Test
    @DisplayName("Should enforce unique constraint on githubRepoId")
    void shouldEnforceUniqueConstraintOnGithubRepoId() {
        Long githubRepoId = 99887766L;

        GitHubRepository repo1 = GitHubRepository.builder()
                .user(sampleUser)
                .connection(sampleConnection)
                .githubRepoId(githubRepoId)
                .name("repo-one")
                .fullName("tester-org/repo-one")
                .ownerLogin("tester-org")
                .htmlUrl("https://github.com/tester-org/repo-one")
                .cloneUrl("https://github.com/tester-org/repo-one.git")
                .defaultBranch("main")
                .isPrivate(false)
                .build();

        repositoryRepository.saveAndFlush(repo1);

        GitHubRepository repo2 = GitHubRepository.builder()
                .user(sampleUser)
                .connection(sampleConnection)
                .githubRepoId(githubRepoId)
                .name("repo-duplicate")
                .fullName("tester-org/repo-duplicate")
                .ownerLogin("tester-org")
                .htmlUrl("https://github.com/tester-org/repo-duplicate")
                .cloneUrl("https://github.com/tester-org/repo-duplicate.git")
                .defaultBranch("main")
                .isPrivate(false)
                .build();

        assertThatThrownBy(() -> repositoryRepository.saveAndFlush(repo2))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Should isolate repositories by user ID")
    void shouldIsolateRepositoriesByUserId() {
        User anotherUser = userRepository.save(User.builder()
                .name("Another User")
                .email("another-" + UUID.randomUUID() + "@shipit.dev")
                .role(Role.ROLE_USER)
                .status(UserStatus.ACTIVE)
                .emailVerified(true)
                .build());

        GitHubRepository repo = repositoryRepository.save(GitHubRepository.builder()
                .user(sampleUser)
                .connection(sampleConnection)
                .githubRepoId(554433L)
                .name("secret-repo")
                .fullName("tester-org/secret-repo")
                .ownerLogin("tester-org")
                .htmlUrl("https://github.com/tester-org/secret-repo")
                .cloneUrl("https://github.com/tester-org/secret-repo.git")
                .defaultBranch("main")
                .isPrivate(true)
                .build());

        // sampleUser should find it
        assertThat(repositoryRepository.findByIdAndUserId(repo.getId(), sampleUser.getId())).isPresent();

        // anotherUser should NOT find it
        assertThat(repositoryRepository.findByIdAndUserId(repo.getId(), anotherUser.getId())).isEmpty();

        List<GitHubRepository> anotherUserRepos = repositoryRepository.findAllByUserId(anotherUser.getId());
        assertThat(anotherUserRepos).isEmpty();
    }

    @Test
    @DisplayName("Should check existsByUserIdAndGithubRepoId correctly")
    void shouldCheckExistsByUserIdAndGithubRepoId() {
        Long repoId = 112233L;

        assertThat(repositoryRepository.existsByUserIdAndGithubRepoId(sampleUser.getId(), repoId)).isFalse();

        repositoryRepository.save(GitHubRepository.builder()
                .user(sampleUser)
                .connection(sampleConnection)
                .githubRepoId(repoId)
                .name("check-repo")
                .fullName("tester-org/check-repo")
                .ownerLogin("tester-org")
                .htmlUrl("https://github.com/tester-org/check-repo")
                .cloneUrl("https://github.com/tester-org/check-repo.git")
                .defaultBranch("main")
                .isPrivate(false)
                .build());

        assertThat(repositoryRepository.existsByUserIdAndGithubRepoId(sampleUser.getId(), repoId)).isTrue();
    }
}

package com.shipit.backend.deployment.repository;

import com.shipit.backend.deployment.entity.Deployment;
import com.shipit.backend.deployment.entity.DeploymentStatus;
import com.shipit.backend.deployment.entity.DeploymentTrigger;
import com.shipit.backend.github.entity.GitHubConnection;
import com.shipit.backend.github.entity.GitHubConnectionStatus;
import com.shipit.backend.github.entity.GitHubRepository;
import com.shipit.backend.github.repository.GitHubConnectionRepository;
import com.shipit.backend.github.repository.GitHubRepositoryRepository;
import com.shipit.backend.project.entity.Project;
import com.shipit.backend.project.entity.ProjectStatus;
import com.shipit.backend.project.repository.ProjectRepository;
import com.shipit.backend.user.entity.Role;
import com.shipit.backend.user.entity.User;
import com.shipit.backend.user.entity.UserStatus;
import com.shipit.backend.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class DeploymentRepositoryTest {

    @Autowired
    private DeploymentRepository deploymentRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private GitHubConnectionRepository connectionRepository;

    @Autowired
    private GitHubRepositoryRepository gitHubRepositoryRepository;

    @Autowired
    private EntityManager entityManager;

    private User sampleUser;
    private User secondUser;
    private Project sampleProject;

    @BeforeEach
    void setUp() {
        sampleUser = userRepository.save(User.builder()
                .name("Deploy Developer")
                .email("deploy-dev-" + UUID.randomUUID() + "@shipit.dev")
                .role(Role.ROLE_USER)
                .status(UserStatus.ACTIVE)
                .emailVerified(true)
                .build());

        secondUser = userRepository.save(User.builder()
                .name("Second Developer")
                .email("second-dev-" + UUID.randomUUID() + "@shipit.dev")
                .role(Role.ROLE_USER)
                .status(UserStatus.ACTIVE)
                .emailVerified(true)
                .build());

        GitHubConnection connection = connectionRepository.save(GitHubConnection.builder()
                .user(sampleUser)
                .installationId(System.currentTimeMillis())
                .status(GitHubConnectionStatus.CONNECTED)
                .connectedAt(Instant.now())
                .accountLogin("shipit-org")
                .build());

        GitHubRepository gitHubRepo = gitHubRepositoryRepository.save(GitHubRepository.builder()
                .user(sampleUser)
                .connection(connection)
                .githubRepoId(System.currentTimeMillis())
                .name("sample-app")
                .fullName("shipit-org/sample-app")
                .ownerLogin("shipit-org")
                .htmlUrl("https://github.com/shipit-org/sample-app")
                .cloneUrl("https://github.com/shipit-org/sample-app.git")
                .defaultBranch("main")
                .isPrivate(false)
                .build());

        sampleProject = projectRepository.save(Project.builder()
                .user(sampleUser)
                .repository(gitHubRepo)
                .name("Sample App")
                .deploymentBranch("main")
                .rootDirectory("/")
                .port(8080)
                .autoDeploy(true)
                .status(ProjectStatus.ACTIVE)
                .build());
    }

    @Test
    @DisplayName("Should persist and retrieve deployment with defaults")
    void shouldPersistAndRetrieveDeploymentWithDefaults() {
        Deployment deployment = Deployment.builder()
                .project(sampleProject)
                .trigger(DeploymentTrigger.MANUAL)
                .commitBranch("main")
                .commitSha("1122334455667788990011223344556677889900")
                .commitMessage("Initial production deployment")
                .commitAuthor("Deploy Developer")
                .build();

        Deployment saved = deploymentRepository.saveAndFlush(deployment);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getStatus()).isEqualTo(DeploymentStatus.QUEUED);
        assertThat(saved.getTrigger()).isEqualTo(DeploymentTrigger.MANUAL);
        assertThat(saved.getQueuedAt()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();

        Optional<Deployment> found = deploymentRepository.findById(saved.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getCommitSha()).isEqualTo("1122334455667788990011223344556677889900");
    }

    @Test
    @DisplayName("Should support pagination when querying project deployments")
    void shouldSupportPaginationWhenQueryingProjectDeployments() {
        for (int i = 1; i <= 5; i++) {
            deploymentRepository.save(Deployment.builder()
                    .project(sampleProject)
                    .trigger(DeploymentTrigger.MANUAL)
                    .commitBranch("main")
                    .commitSha("sha" + i)
                    .commitMessage("Deploy " + i)
                    .build());
        }
        deploymentRepository.flush();

        PageRequest pageRequest = PageRequest.of(0, 2, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Deployment> page = deploymentRepository.findAllByProjectId(sampleProject.getId(), pageRequest);

        assertThat(page.getTotalElements()).isEqualTo(5);
        assertThat(page.getTotalPages()).isEqualTo(3);
        assertThat(page.getContent()).hasSize(2);
    }

    @Test
    @DisplayName("Should isolate deployment retrieval by user ID via findByIdAndUserId")
    void shouldIsolateDeploymentRetrievalByUserId() {
        Deployment deployment = deploymentRepository.saveAndFlush(Deployment.builder()
                .project(sampleProject)
                .trigger(DeploymentTrigger.MANUAL)
                .commitBranch("main")
                .commitSha("abcdef123456")
                .build());

        Optional<Deployment> foundForOwner = deploymentRepository.findByIdAndUserId(deployment.getId(), sampleUser.getId());
        assertThat(foundForOwner).isPresent();

        Optional<Deployment> foundForOther = deploymentRepository.findByIdAndUserId(deployment.getId(), secondUser.getId());
        assertThat(foundForOther).isEmpty();
    }

    @Test
    @DisplayName("Should check if project has active deployment via existsByProjectIdAndStatusIn")
    void shouldCheckActiveDeployments() {
        deploymentRepository.saveAndFlush(Deployment.builder()
                .project(sampleProject)
                .trigger(DeploymentTrigger.MANUAL)
                .commitBranch("main")
                .status(DeploymentStatus.BUILDING)
                .build());

        boolean hasActive = deploymentRepository.existsByProjectIdAndStatusIn(
                sampleProject.getId(),
                Set.of(DeploymentStatus.QUEUED, DeploymentStatus.CLONING, DeploymentStatus.BUILDING)
        );
        assertThat(hasActive).isTrue();

        boolean hasSuccess = deploymentRepository.existsByProjectIdAndStatusIn(
                sampleProject.getId(),
                Set.of(DeploymentStatus.SUCCESS, DeploymentStatus.FAILED)
        );
        assertThat(hasSuccess).isFalse();
    }

    @Test
    @DisplayName("Should find latest deployment for project")
    void shouldFindLatestDeploymentForProject() {
        Deployment d1 = deploymentRepository.save(Deployment.builder()
                .project(sampleProject)
                .trigger(DeploymentTrigger.MANUAL)
                .commitBranch("main")
                .commitSha("sha1")
                .build());

        Deployment d2 = deploymentRepository.save(Deployment.builder()
                .project(sampleProject)
                .trigger(DeploymentTrigger.MANUAL)
                .commitBranch("main")
                .commitSha("sha2")
                .build());
        deploymentRepository.flush();

        Optional<Deployment> latest = deploymentRepository.findTopByProjectIdOrderByCreatedAtDesc(sampleProject.getId());
        assertThat(latest).isPresent();
        assertThat(latest.get().getCommitSha()).isEqualTo("sha2");
    }

    @Test
    @DisplayName("Should cascade delete deployments when associated Project is deleted")
    void shouldCascadeDeleteDeploymentsWhenProjectIsDeleted() {
        Deployment d1 = deploymentRepository.save(Deployment.builder()
                .project(sampleProject)
                .trigger(DeploymentTrigger.MANUAL)
                .commitBranch("main")
                .commitSha("sha1")
                .build());
        deploymentRepository.flush();

        UUID depId = d1.getId();
        assertThat(deploymentRepository.existsById(depId)).isTrue();

        // Delete project
        projectRepository.delete(sampleProject);
        projectRepository.flush();
        entityManager.clear();

        assertThat(deploymentRepository.existsById(depId)).isFalse();
    }
}

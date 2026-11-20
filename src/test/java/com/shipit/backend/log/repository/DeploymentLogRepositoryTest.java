package com.shipit.backend.log.repository;

import com.shipit.backend.build.logging.LogSource;
import com.shipit.backend.deployment.entity.Deployment;
import com.shipit.backend.deployment.entity.DeploymentStatus;
import com.shipit.backend.deployment.entity.DeploymentTrigger;
import com.shipit.backend.deployment.repository.DeploymentRepository;
import com.shipit.backend.github.entity.GitHubConnection;
import com.shipit.backend.github.entity.GitHubConnectionStatus;
import com.shipit.backend.github.entity.GitHubRepository;
import com.shipit.backend.github.repository.GitHubConnectionRepository;
import com.shipit.backend.github.repository.GitHubRepositoryRepository;
import com.shipit.backend.log.entity.DeploymentLog;
import com.shipit.backend.log.entity.DeploymentStage;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class DeploymentLogRepositoryTest {

    @Autowired
    private DeploymentLogRepository logRepository;

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

    private Deployment sampleDeployment;

    @BeforeEach
    void setUp() {
        User user = userRepository.save(User.builder()
                .name("Log Developer")
                .email("log-dev-" + UUID.randomUUID() + "@shipit.dev")
                .role(Role.ROLE_USER)
                .status(UserStatus.ACTIVE)
                .emailVerified(true)
                .build());

        GitHubConnection connection = connectionRepository.save(GitHubConnection.builder()
                .user(user)
                .installationId(System.currentTimeMillis())
                .status(GitHubConnectionStatus.CONNECTED)
                .connectedAt(Instant.now())
                .build());

        GitHubRepository gitHubRepo = gitHubRepositoryRepository.save(GitHubRepository.builder()
                .user(user)
                .connection(connection)
                .githubRepoId(System.currentTimeMillis())
                .name("test-log-repo")
                .fullName("log-org/test-log-repo")
                .ownerLogin("log-org")
                .isPrivate(false)
                .defaultBranch("main")
                .htmlUrl("https://github.com/log-org/test-log-repo")
                .cloneUrl("https://github.com/log-org/test-log-repo.git")
                .build());

        Project project = projectRepository.save(Project.builder()
                .user(user)
                .repository(gitHubRepo)
                .name("Test Log App")
                .normalizedName("test-log-app")
                .deploymentBranch("main")
                .rootDirectory("/")
                .port(8080)
                .autoDeploy(true)
                .status(ProjectStatus.ACTIVE)
                .build());

        sampleDeployment = deploymentRepository.save(Deployment.builder()
                .project(project)
                .status(DeploymentStatus.BUILDING)
                .trigger(DeploymentTrigger.MANUAL)
                .commitBranch("main")
                .commitSha("abc123456789")
                .commitMessage("Initial commit")
                .build());
    }

    @Test
    @DisplayName("save & findByDeploymentIdOrderBySequenceNumberAsc - Retrieves logs in chronological sequence order")
    void saveAndFindByDeploymentId_Ordered() {
        DeploymentLog log1 = logRepository.save(DeploymentLog.builder()
                .deployment(sampleDeployment)
                .sequenceNumber(1L)
                .stage(DeploymentStage.CLONING)
                .source(LogSource.SYSTEM)
                .message("Cloning repository...")
                .build());

        DeploymentLog log2 = logRepository.save(DeploymentLog.builder()
                .deployment(sampleDeployment)
                .sequenceNumber(2L)
                .stage(DeploymentStage.BUILDING)
                .source(LogSource.STDOUT)
                .message("Running mvn clean package")
                .build());

        List<DeploymentLog> logs = logRepository.findByDeploymentIdOrderBySequenceNumberAsc(sampleDeployment.getId());

        assertThat(logs).hasSize(2);
        assertThat(logs.get(0).getSequenceNumber()).isEqualTo(1L);
        assertThat(logs.get(0).getMessage()).isEqualTo("Cloning repository...");
        assertThat(logs.get(1).getSequenceNumber()).isEqualTo(2L);
        assertThat(logs.get(1).getMessage()).isEqualTo("Running mvn clean package");
    }

    @Test
    @DisplayName("findByDeploymentIdOrderBySequenceNumberAsc - Supports pagination")
    void findByDeploymentId_Paginated() {
        for (int i = 1; i <= 5; i++) {
            logRepository.save(DeploymentLog.builder()
                    .deployment(sampleDeployment)
                    .sequenceNumber((long) i)
                    .stage(DeploymentStage.BUILDING)
                    .source(LogSource.STDOUT)
                    .message("Build step " + i)
                    .build());
        }

        List<DeploymentLog> page1 = logRepository.findByDeploymentIdOrderBySequenceNumberAsc(
                sampleDeployment.getId(), PageRequest.of(0, 2)
        );
        assertThat(page1).hasSize(2);
        assertThat(page1.get(0).getSequenceNumber()).isEqualTo(1L);
        assertThat(page1.get(1).getSequenceNumber()).isEqualTo(2L);

        List<DeploymentLog> page2 = logRepository.findByDeploymentIdOrderBySequenceNumberAsc(
                sampleDeployment.getId(), PageRequest.of(1, 2)
        );
        assertThat(page2).hasSize(2);
        assertThat(page2.get(0).getSequenceNumber()).isEqualTo(3L);
        assertThat(page2.get(1).getSequenceNumber()).isEqualTo(4L);
    }

    @Test
    @DisplayName("findByDeploymentIdAndStageOrderBySequenceNumberAsc - Filters logs by execution stage")
    void findByDeploymentIdAndStage_Filter() {
        logRepository.save(DeploymentLog.builder()
                .deployment(sampleDeployment)
                .sequenceNumber(1L)
                .stage(DeploymentStage.CLONING)
                .source(LogSource.SYSTEM)
                .message("Clone step")
                .build());

        logRepository.save(DeploymentLog.builder()
                .deployment(sampleDeployment)
                .sequenceNumber(2L)
                .stage(DeploymentStage.BUILDING)
                .source(LogSource.STDOUT)
                .message("Build step")
                .build());

        List<DeploymentLog> buildLogs = logRepository.findByDeploymentIdAndStageOrderBySequenceNumberAsc(
                sampleDeployment.getId(), DeploymentStage.BUILDING, PageRequest.of(0, 10)
        );

        assertThat(buildLogs).hasSize(1);
        assertThat(buildLogs.get(0).getMessage()).isEqualTo("Build step");
        assertThat(buildLogs.get(0).getStage()).isEqualTo(DeploymentStage.BUILDING);
    }

    @Test
    @DisplayName("findByDeploymentIdAndSequenceNumberGreaterThan - Cursor-based incremental polling")
    void findByDeploymentIdAndSequenceNumberGreaterThan_Cursor() {
        for (int i = 1; i <= 4; i++) {
            logRepository.save(DeploymentLog.builder()
                    .deployment(sampleDeployment)
                    .sequenceNumber((long) i)
                    .stage(DeploymentStage.BUILDING)
                    .source(LogSource.STDOUT)
                    .message("Step " + i)
                    .build());
        }

        List<DeploymentLog> nextLogs = logRepository.findByDeploymentIdAndSequenceNumberGreaterThanOrderBySequenceNumberAsc(
                sampleDeployment.getId(), 2L, PageRequest.of(0, 10)
        );

        assertThat(nextLogs).hasSize(2);
        assertThat(nextLogs.get(0).getSequenceNumber()).isEqualTo(3L);
        assertThat(nextLogs.get(1).getSequenceNumber()).isEqualTo(4L);
    }

    @Test
    @DisplayName("countByDeploymentId & findMaxSequenceNumberByDeploymentId - Calculates totals and max sequence")
    void countAndMaxSequence() {
        assertThat(logRepository.countByDeploymentId(sampleDeployment.getId())).isEqualTo(0L);
        assertThat(logRepository.findMaxSequenceNumberByDeploymentId(sampleDeployment.getId())).isEqualTo(0L);

        logRepository.save(DeploymentLog.builder()
                .deployment(sampleDeployment)
                .sequenceNumber(10L)
                .stage(DeploymentStage.CLONING)
                .source(LogSource.SYSTEM)
                .message("First log")
                .build());

        logRepository.save(DeploymentLog.builder()
                .deployment(sampleDeployment)
                .sequenceNumber(25L)
                .stage(DeploymentStage.STARTING)
                .source(LogSource.SYSTEM)
                .message("Second log")
                .build());

        assertThat(logRepository.countByDeploymentId(sampleDeployment.getId())).isEqualTo(2L);
        assertThat(logRepository.countByDeploymentIdAndStage(sampleDeployment.getId(), DeploymentStage.CLONING)).isEqualTo(1L);
        assertThat(logRepository.findMaxSequenceNumberByDeploymentId(sampleDeployment.getId())).isEqualTo(25L);
    }

    @Test
    @DisplayName("deleteByDeploymentId - Removes all logs for specified deployment")
    void deleteByDeploymentId() {
        logRepository.save(DeploymentLog.builder()
                .deployment(sampleDeployment)
                .sequenceNumber(1L)
                .stage(DeploymentStage.CLONING)
                .source(LogSource.SYSTEM)
                .message("To be deleted")
                .build());

        entityManager.flush();
        entityManager.clear();

        assertThat(logRepository.countByDeploymentId(sampleDeployment.getId())).isEqualTo(1L);

        logRepository.deleteByDeploymentId(sampleDeployment.getId());
        entityManager.flush();
        entityManager.clear();

        assertThat(logRepository.countByDeploymentId(sampleDeployment.getId())).isEqualTo(0L);
    }
}

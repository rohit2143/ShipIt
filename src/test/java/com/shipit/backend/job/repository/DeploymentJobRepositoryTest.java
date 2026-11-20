package com.shipit.backend.job.repository;

import com.shipit.backend.deployment.entity.Deployment;
import com.shipit.backend.deployment.entity.DeploymentStatus;
import com.shipit.backend.deployment.entity.DeploymentTrigger;
import com.shipit.backend.deployment.repository.DeploymentRepository;
import com.shipit.backend.github.entity.GitHubConnection;
import com.shipit.backend.github.entity.GitHubConnectionStatus;
import com.shipit.backend.github.entity.GitHubRepository;
import com.shipit.backend.github.repository.GitHubConnectionRepository;
import com.shipit.backend.github.repository.GitHubRepositoryRepository;
import com.shipit.backend.job.entity.DeploymentJob;
import com.shipit.backend.job.entity.JobStatus;
import com.shipit.backend.job.entity.JobType;
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
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class DeploymentJobRepositoryTest {

    @Autowired
    private DeploymentJobRepository jobRepository;

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
    private User sampleUser;

    @BeforeEach
    void setUp() {
        sampleUser = userRepository.save(User.builder()
                .name("Job Developer")
                .email("job-dev-" + UUID.randomUUID() + "@shipit.dev")
                .role(Role.ROLE_USER)
                .status(UserStatus.ACTIVE)
                .emailVerified(true)
                .build());

        GitHubConnection connection = connectionRepository.save(GitHubConnection.builder()
                .user(sampleUser)
                .installationId(System.currentTimeMillis())
                .status(GitHubConnectionStatus.CONNECTED)
                .connectedAt(Instant.now())
                .accountLogin("job-gh-org")
                .build());

        GitHubRepository gitHubRepo = gitHubRepositoryRepository.save(GitHubRepository.builder()
                .user(sampleUser)
                .connection(connection)
                .githubRepoId(System.currentTimeMillis())
                .name("job-sample-app")
                .fullName("job-gh-org/job-sample-app")
                .ownerLogin("job-gh-org")
                .htmlUrl("https://github.com/job-gh-org/job-sample-app")
                .cloneUrl("https://github.com/job-gh-org/job-sample-app.git")
                .defaultBranch("main")
                .isPrivate(false)
                .build());

        Project project = projectRepository.save(Project.builder()
                .user(sampleUser)
                .repository(gitHubRepo)
                .name("Job Service Project")
                .deploymentBranch("main")
                .rootDirectory("/")
                .port(8080)
                .autoDeploy(true)
                .status(ProjectStatus.ACTIVE)
                .build());

        sampleDeployment = deploymentRepository.save(Deployment.builder()
                .project(project)
                .status(DeploymentStatus.QUEUED)
                .trigger(DeploymentTrigger.MANUAL)
                .commitBranch("main")
                .commitSha("abcdef1234567890abcdef1234567890abcdef12")
                .commitMessage("Initial job deployment test")
                .queuedAt(Instant.now())
                .build());

        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("Saves and retrieves a DeploymentJob with all audit fields")
    void testSaveAndRetrieveJob() {
        DeploymentJob job = DeploymentJob.builder()
                .deployment(sampleDeployment)
                .type(JobType.DEPLOYMENT)
                .status(JobStatus.PENDING)
                .priority(5)
                .maxRetries(3)
                .queuedAt(Instant.now())
                .build();

        DeploymentJob saved = jobRepository.save(job);
        entityManager.flush();
        entityManager.clear();

        Optional<DeploymentJob> retrieved = jobRepository.findById(saved.getId());
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().getDeployment().getId()).isEqualTo(sampleDeployment.getId());
        assertThat(retrieved.get().getStatus()).isEqualTo(JobStatus.PENDING);
        assertThat(retrieved.get().getPriority()).isEqualTo(5);
        assertThat(retrieved.get().getRetryCount()).isEqualTo(0);
        assertThat(retrieved.get().getCreatedAt()).isNotNull();
        assertThat(retrieved.get().getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("findNextPendingJobForUpdate locks and retrieves highest priority oldest PENDING job")
    void testFindNextPendingJobForUpdate() {
        Instant now = Instant.now();

        // Low priority, queued earlier
        DeploymentJob jobLow = DeploymentJob.builder()
                .deployment(sampleDeployment)
                .type(JobType.DEPLOYMENT)
                .status(JobStatus.PENDING)
                .priority(0)
                .queuedAt(now.minusSeconds(100))
                .build();
        jobRepository.save(jobLow);

        // High priority, queued later
        DeploymentJob jobHigh = DeploymentJob.builder()
                .deployment(sampleDeployment)
                .type(JobType.DEPLOYMENT)
                .status(JobStatus.PENDING)
                .priority(10)
                .queuedAt(now.minusSeconds(10))
                .build();
        jobRepository.save(jobHigh);

        // Already running job, should be skipped
        DeploymentJob jobRunning = DeploymentJob.builder()
                .deployment(sampleDeployment)
                .type(JobType.DEPLOYMENT)
                .status(JobStatus.RUNNING)
                .priority(20)
                .queuedAt(now.minusSeconds(200))
                .build();
        jobRepository.save(jobRunning);

        entityManager.flush();
        entityManager.clear();

        Optional<DeploymentJob> nextJob = jobRepository.findNextPendingJobForUpdate();
        assertThat(nextJob).isPresent();
        assertThat(nextJob.get().getId()).isEqualTo(jobHigh.getId());
        assertThat(nextJob.get().getPriority()).isEqualTo(10);
    }

    @Test
    @DisplayName("existsByDeploymentIdAndStatusIn returns true if active job exists for deployment")
    void testExistsByDeploymentIdAndStatusIn() {
        DeploymentJob job = DeploymentJob.builder()
                .deployment(sampleDeployment)
                .type(JobType.DEPLOYMENT)
                .status(JobStatus.RUNNING)
                .queuedAt(Instant.now())
                .build();
        jobRepository.save(job);
        entityManager.flush();
        entityManager.clear();

        boolean existsActive = jobRepository.existsByDeploymentIdAndStatusIn(
                sampleDeployment.getId(),
                Set.of(JobStatus.PENDING, JobStatus.RUNNING)
        );
        assertThat(existsActive).isTrue();

        boolean existsTerminal = jobRepository.existsByDeploymentIdAndStatusIn(
                sampleDeployment.getId(),
                Set.of(JobStatus.COMPLETED, JobStatus.FAILED)
        );
        assertThat(existsTerminal).isFalse();
    }

    @Test
    @DisplayName("findByStatusAndHeartbeatAtBefore finds stale jobs past heartbeat threshold")
    void testFindByStatusAndHeartbeatAtBefore() {
        Instant threshold = Instant.now().minusSeconds(180);

        // Active job with fresh heartbeat
        DeploymentJob freshJob = DeploymentJob.builder()
                .deployment(sampleDeployment)
                .type(JobType.DEPLOYMENT)
                .status(JobStatus.RUNNING)
                .claimedBy("worker-active")
                .claimedAt(Instant.now().minusSeconds(60))
                .heartbeatAt(Instant.now().minusSeconds(10))
                .queuedAt(Instant.now().minusSeconds(120))
                .build();
        jobRepository.save(freshJob);

        // Stale job with expired heartbeat
        DeploymentJob staleJob = DeploymentJob.builder()
                .deployment(sampleDeployment)
                .type(JobType.DEPLOYMENT)
                .status(JobStatus.RUNNING)
                .claimedBy("worker-crashed")
                .claimedAt(Instant.now().minusSeconds(300))
                .heartbeatAt(Instant.now().minusSeconds(250))
                .queuedAt(Instant.now().minusSeconds(400))
                .build();
        jobRepository.save(staleJob);

        entityManager.flush();
        entityManager.clear();

        List<DeploymentJob> staleJobs = jobRepository.findByStatusAndHeartbeatAtBefore(JobStatus.RUNNING, threshold);
        assertThat(staleJobs).hasSize(1);
        assertThat(staleJobs.get(0).getId()).isEqualTo(staleJob.getId());
        assertThat(staleJobs.get(0).getClaimedBy()).isEqualTo("worker-crashed");
    }

    @Test
    @DisplayName("Cascades delete when associated deployment is deleted")
    void testCascadeDelete() {
        DeploymentJob job = DeploymentJob.builder()
                .deployment(sampleDeployment)
                .type(JobType.DEPLOYMENT)
                .status(JobStatus.PENDING)
                .queuedAt(Instant.now())
                .build();
        DeploymentJob savedJob = jobRepository.save(job);
        entityManager.flush();
        entityManager.clear();

        // Delete deployment
        deploymentRepository.deleteById(sampleDeployment.getId());
        entityManager.flush();
        entityManager.clear();

        assertThat(jobRepository.findById(savedJob.getId())).isEmpty();
    }

    @Test
    @DisplayName("countByStatus and findAllByStatus return accurate counts and lists")
    void testCountAndFindAllByStatus() {
        jobRepository.deleteAll();
        entityManager.flush();
        entityManager.clear();

        DeploymentJob pending1 = DeploymentJob.builder()
                .deployment(sampleDeployment)
                .type(JobType.DEPLOYMENT)
                .status(JobStatus.PENDING)
                .queuedAt(Instant.now())
                .build();
        DeploymentJob pending2 = DeploymentJob.builder()
                .deployment(sampleDeployment)
                .type(JobType.DEPLOYMENT)
                .status(JobStatus.PENDING)
                .queuedAt(Instant.now())
                .build();
        DeploymentJob completed = DeploymentJob.builder()
                .deployment(sampleDeployment)
                .type(JobType.DEPLOYMENT)
                .status(JobStatus.COMPLETED)
                .queuedAt(Instant.now())
                .build();

        jobRepository.saveAll(List.of(pending1, pending2, completed));
        entityManager.flush();
        entityManager.clear();

        assertThat(jobRepository.countByStatus(JobStatus.PENDING)).isEqualTo(2);
        assertThat(jobRepository.countByStatus(JobStatus.COMPLETED)).isEqualTo(1);
        assertThat(jobRepository.findAllByStatus(JobStatus.PENDING)).hasSize(2);
    }

    @Test
    @DisplayName("findByIdAndUserId verifies tenancy through deployment and project")
    void testFindByIdAndUserId() {
        DeploymentJob job = DeploymentJob.builder()
                .deployment(sampleDeployment)
                .type(JobType.DEPLOYMENT)
                .status(JobStatus.PENDING)
                .queuedAt(Instant.now())
                .build();
        DeploymentJob saved = jobRepository.save(job);
        entityManager.flush();
        entityManager.clear();

        // Matching user
        Optional<DeploymentJob> found = jobRepository.findByIdAndUserId(saved.getId(), sampleUser.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(saved.getId());

        // Other user
        UUID otherUserId = UUID.randomUUID();
        Optional<DeploymentJob> notFound = jobRepository.findByIdAndUserId(saved.getId(), otherUserId);
        assertThat(notFound).isEmpty();
    }

    @Test
    @DisplayName("findAllByDeploymentIdAndUserIdOrderByCreatedAtDesc filters by user and orders newest first")
    void testFindAllByDeploymentIdAndUserIdOrderByCreatedAtDesc() {
        DeploymentJob job1 = DeploymentJob.builder()
                .deployment(sampleDeployment)
                .type(JobType.DEPLOYMENT)
                .status(JobStatus.COMPLETED)
                .queuedAt(Instant.now().minusSeconds(100))
                .build();
        DeploymentJob job2 = DeploymentJob.builder()
                .deployment(sampleDeployment)
                .type(JobType.DEPLOYMENT)
                .status(JobStatus.PENDING)
                .queuedAt(Instant.now())
                .build();

        jobRepository.save(job1);
        jobRepository.save(job2);
        entityManager.flush();
        entityManager.clear();

        List<DeploymentJob> jobs = jobRepository.findAllByDeploymentIdAndUserIdOrderByCreatedAtDesc(
                sampleDeployment.getId(), sampleUser.getId()
        );

        assertThat(jobs).hasSize(2);
        assertThat(jobs.get(0).getId()).isEqualTo(job2.getId());
        assertThat(jobs.get(1).getId()).isEqualTo(job1.getId());

        // Other user gets empty list
        List<DeploymentJob> empty = jobRepository.findAllByDeploymentIdAndUserIdOrderByCreatedAtDesc(
                sampleDeployment.getId(), UUID.randomUUID()
        );
        assertThat(empty).isEmpty();
    }
}

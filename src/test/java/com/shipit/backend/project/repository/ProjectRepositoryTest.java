package com.shipit.backend.project.repository;

import com.shipit.backend.github.entity.GitHubConnection;
import com.shipit.backend.github.entity.GitHubConnectionStatus;
import com.shipit.backend.github.entity.GitHubRepository;
import com.shipit.backend.github.repository.GitHubConnectionRepository;
import com.shipit.backend.github.repository.GitHubRepositoryRepository;
import com.shipit.backend.project.entity.Project;
import com.shipit.backend.project.entity.ProjectStatus;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class ProjectRepositoryTest {

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
    private GitHubRepository sampleRepo;

    @BeforeEach
    void setUp() {
        sampleUser = userRepository.save(User.builder()
                .name("Project Developer")
                .email("dev-" + UUID.randomUUID() + "@shipit.dev")
                .role(Role.ROLE_USER)
                .status(UserStatus.ACTIVE)
                .emailVerified(true)
                .build());

        secondUser = userRepository.save(User.builder()
                .name("Second Developer")
                .email("dev2-" + UUID.randomUUID() + "@shipit.dev")
                .role(Role.ROLE_USER)
                .status(UserStatus.ACTIVE)
                .emailVerified(true)
                .build());

        GitHubConnection connection = connectionRepository.save(GitHubConnection.builder()
                .user(sampleUser)
                .installationId(System.currentTimeMillis())
                .status(GitHubConnectionStatus.CONNECTED)
                .connectedAt(Instant.now())
                .accountLogin("shipit-devs")
                .build());

        sampleRepo = gitHubRepositoryRepository.save(GitHubRepository.builder()
                .user(sampleUser)
                .connection(connection)
                .githubRepoId(System.currentTimeMillis())
                .name("ShipIt")
                .fullName("shipit-devs/ShipIt")
                .ownerLogin("shipit-devs")
                .htmlUrl("https://github.com/shipit-devs/ShipIt")
                .cloneUrl("https://github.com/shipit-devs/ShipIt.git")
                .defaultBranch("main")
                .isPrivate(false)
                .build());
    }

    @Test
    @DisplayName("Should persist and retrieve Project with defaults and synchronized normalized name")
    void shouldPersistAndRetrieveProjectWithDefaults() {
        Project project = Project.builder()
                .user(sampleUser)
                .repository(sampleRepo)
                .name("My App Backend")
                .deploymentBranch("main")
                .build();

        Project saved = projectRepository.saveAndFlush(project);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getName()).isEqualTo("My App Backend");
        assertThat(saved.getNormalizedName()).isEqualTo("my app backend");
        assertThat(saved.getPort()).isEqualTo(8080);
        assertThat(saved.getRootDirectory()).isEqualTo("/");
        assertThat(saved.isAutoDeploy()).isTrue();
        assertThat(saved.getStatus()).isEqualTo(ProjectStatus.ACTIVE);
    }

    @Test
    @DisplayName("Should enforce case-insensitive uniqueness of project name for the same user")
    void shouldEnforceCaseInsensitiveUniquenessForSameUser() {
        Project p1 = Project.builder()
                .user(sampleUser)
                .repository(sampleRepo)
                .name("ShipIt-Core")
                .deploymentBranch("main")
                .build();
        projectRepository.saveAndFlush(p1);

        Project p2 = Project.builder()
                .user(sampleUser)
                .repository(sampleRepo)
                .name("shipit-core")
                .deploymentBranch("develop")
                .build();

        assertThatThrownBy(() -> projectRepository.saveAndFlush(p2))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Should allow same project name across different users (multi-tenant isolation)")
    void shouldAllowSameProjectNameAcrossDifferentUsers() {
        Project p1 = Project.builder()
                .user(sampleUser)
                .repository(sampleRepo)
                .name("Awesome-App")
                .deploymentBranch("main")
                .build();
        projectRepository.saveAndFlush(p1);

        // Different user can have a project with the same name
        Project p2 = Project.builder()
                .user(secondUser)
                .repository(sampleRepo)
                .name("awesome-app")
                .deploymentBranch("main")
                .build();

        Project savedSecond = projectRepository.saveAndFlush(p2);
        assertThat(savedSecond.getId()).isNotNull();
        assertThat(savedSecond.getUser().getId()).isEqualTo(secondUser.getId());
    }

    @Test
    @DisplayName("Should enforce port range check constraint")
    void shouldEnforcePortRangeCheckConstraint() {
        Project invalidLowPort = Project.builder()
                .user(sampleUser)
                .repository(sampleRepo)
                .name("Invalid Low Port App")
                .deploymentBranch("main")
                .port(0)
                .build();

        assertThatThrownBy(() -> projectRepository.saveAndFlush(invalidLowPort))
                .isInstanceOf(Exception.class);

        entityManager.clear();

        Project invalidHighPort = Project.builder()
                .user(sampleUser)
                .repository(sampleRepo)
                .name("Invalid High Port App")
                .deploymentBranch("main")
                .port(70000)
                .build();

        assertThatThrownBy(() -> projectRepository.saveAndFlush(invalidHighPort))
                .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("Should find multiple active auto-deploy projects matching repo and branch for webhook triggers")
    void shouldFindActiveAutoDeployProjectsForWebhook() {
        // Project 1: Active, auto-deploy on main -> MATCH
        Project p1 = Project.builder()
                .user(sampleUser)
                .repository(sampleRepo)
                .name("Backend Service")
                .deploymentBranch("main")
                .autoDeploy(true)
                .status(ProjectStatus.ACTIVE)
                .build();
        projectRepository.save(p1);

        // Project 2: Active, auto-deploy on main (monorepo frontend) -> MATCH
        Project p2 = Project.builder()
                .user(sampleUser)
                .repository(sampleRepo)
                .name("Frontend Service")
                .deploymentBranch("main")
                .autoDeploy(true)
                .status(ProjectStatus.ACTIVE)
                .build();
        projectRepository.save(p2);

        // Project 3: Archived on main -> SHOULD NOT MATCH
        Project p3 = Project.builder()
                .user(sampleUser)
                .repository(sampleRepo)
                .name("Old Service")
                .deploymentBranch("main")
                .autoDeploy(true)
                .status(ProjectStatus.ARCHIVED)
                .build();
        projectRepository.save(p3);

        // Project 4: Active on main, autoDeploy=false -> SHOULD NOT MATCH
        Project p4 = Project.builder()
                .user(sampleUser)
                .repository(sampleRepo)
                .name("Manual Service")
                .deploymentBranch("main")
                .autoDeploy(false)
                .status(ProjectStatus.ACTIVE)
                .build();
        projectRepository.save(p4);

        // Project 5: Active, auto-deploy on develop -> SHOULD NOT MATCH
        Project p5 = Project.builder()
                .user(sampleUser)
                .repository(sampleRepo)
                .name("Dev Service")
                .deploymentBranch("develop")
                .autoDeploy(true)
                .status(ProjectStatus.ACTIVE)
                .build();
        projectRepository.save(p5);

        projectRepository.flush();

        List<Project> matches = projectRepository.findActiveAutoDeployProjects(
                sampleRepo.getId(),
                "main",
                ProjectStatus.ACTIVE
        );

        assertThat(matches).hasSize(2);
        assertThat(matches).extracting(Project::getName)
                .containsExactlyInAnyOrder("Backend Service", "Frontend Service");
    }

    @Test
    @DisplayName("Should strictly isolate projects by userId")
    void shouldStrictlyIsolateProjectsByUserId() {
        Project project = Project.builder()
                .user(sampleUser)
                .repository(sampleRepo)
                .name("Isolated Project")
                .deploymentBranch("main")
                .build();
        Project saved = projectRepository.saveAndFlush(project);

        Optional<Project> foundByUser1 = projectRepository.findByIdAndUserId(saved.getId(), sampleUser.getId());
        assertThat(foundByUser1).isPresent();
        assertThat(foundByUser1.get().getName()).isEqualTo("Isolated Project");

        Optional<Project> foundByUser2 = projectRepository.findByIdAndUserId(saved.getId(), secondUser.getId());
        assertThat(foundByUser2).isEmpty();
    }

    @Test
    @DisplayName("Should check existence by normalized name using Locale.ROOT")
    void shouldCheckExistenceByNormalizedName() {
        Project project = Project.builder()
                .user(sampleUser)
                .repository(sampleRepo)
                .name("My Web API")
                .deploymentBranch("main")
                .build();
        Project saved = projectRepository.saveAndFlush(project);

        String testName = "  MY WEB API  ".trim().toLowerCase(Locale.ROOT);
        boolean exists = projectRepository.existsByUserIdAndNormalizedName(sampleUser.getId(), testName);
        assertThat(exists).isTrue();

        boolean existsForOtherUser = projectRepository.existsByUserIdAndNormalizedName(secondUser.getId(), testName);
        assertThat(existsForOtherUser).isFalse();

        // For updates: exists excluding self ID
        boolean existsExcludingSelf = projectRepository.existsByUserIdAndNormalizedNameAndIdNot(
                sampleUser.getId(), testName, saved.getId()
        );
        assertThat(existsExcludingSelf).isFalse();

        // But true when checking against a random other ID
        boolean existsExcludingOther = projectRepository.existsByUserIdAndNormalizedNameAndIdNot(
                sampleUser.getId(), testName, UUID.randomUUID()
        );
        assertThat(existsExcludingOther).isTrue();
    }
}

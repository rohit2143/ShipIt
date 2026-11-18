package com.shipit.backend.project.repository;

import com.shipit.backend.github.entity.GitHubConnection;
import com.shipit.backend.github.entity.GitHubConnectionStatus;
import com.shipit.backend.github.entity.GitHubRepository;
import com.shipit.backend.github.repository.GitHubConnectionRepository;
import com.shipit.backend.github.repository.GitHubRepositoryRepository;
import com.shipit.backend.project.entity.Project;
import com.shipit.backend.project.entity.ProjectEnvironmentVariable;
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
class ProjectEnvironmentVariableRepositoryTest {

    @Autowired
    private ProjectEnvironmentVariableRepository envVarRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private GitHubConnectionRepository connectionRepository;

    @Autowired
    private GitHubRepositoryRepository gitHubRepositoryRepository;

    private User sampleUser;
    private Project sampleProject;
    private Project secondProject;

    @BeforeEach
    void setUp() {
        sampleUser = userRepository.save(User.builder()
                .name("Env Developer")
                .email("env-dev-" + UUID.randomUUID() + "@shipit.dev")
                .role(Role.ROLE_USER)
                .status(UserStatus.ACTIVE)
                .emailVerified(true)
                .build());

        GitHubConnection connection = connectionRepository.save(GitHubConnection.builder()
                .user(sampleUser)
                .installationId(System.currentTimeMillis())
                .status(GitHubConnectionStatus.CONNECTED)
                .connectedAt(Instant.now())
                .accountLogin("env-org")
                .build());

        GitHubRepository repo = gitHubRepositoryRepository.save(GitHubRepository.builder()
                .user(sampleUser)
                .connection(connection)
                .githubRepoId(System.currentTimeMillis())
                .name("ShipIt-App")
                .fullName("env-org/ShipIt-App")
                .ownerLogin("env-org")
                .htmlUrl("https://github.com/env-org/ShipIt-App")
                .cloneUrl("https://github.com/env-org/ShipIt-App.git")
                .defaultBranch("main")
                .isPrivate(false)
                .build());

        sampleProject = projectRepository.save(Project.builder()
                .user(sampleUser)
                .repository(repo)
                .name("Project Alpha")
                .deploymentBranch("main")
                .build());

        secondProject = projectRepository.save(Project.builder()
                .user(sampleUser)
                .repository(repo)
                .name("Project Beta")
                .deploymentBranch("main")
                .build());
    }

    @Test
    @DisplayName("Should persist and retrieve environment variables for a project")
    void shouldPersistAndRetrieveEnvVars() {
        ProjectEnvironmentVariable v1 = ProjectEnvironmentVariable.builder()
                .project(sampleProject)
                .key("NODE_ENV")
                .value("production")
                .isSecret(false)
                .build();

        ProjectEnvironmentVariable v2 = ProjectEnvironmentVariable.builder()
                .project(sampleProject)
                .key("DB_PASSWORD")
                .value("v1:base64encryptedPayload==")
                .isSecret(true)
                .build();

        envVarRepository.save(v1);
        envVarRepository.save(v2);
        envVarRepository.flush();

        List<ProjectEnvironmentVariable> vars = envVarRepository.findAllByProjectId(sampleProject.getId());
        assertThat(vars).hasSize(2);
        assertThat(vars).extracting(ProjectEnvironmentVariable::getKey)
                .containsExactlyInAnyOrder("NODE_ENV", "DB_PASSWORD");

        Optional<ProjectEnvironmentVariable> dbPassOpt =
                envVarRepository.findByProjectIdAndKey(sampleProject.getId(), "DB_PASSWORD");
        assertThat(dbPassOpt).isPresent();
        assertThat(dbPassOpt.get().isSecret()).isTrue();
        assertThat(dbPassOpt.get().getValue()).isEqualTo("v1:base64encryptedPayload==");
    }

    @Test
    @DisplayName("Should enforce key uniqueness per project")
    void shouldEnforceKeyUniquenessPerProject() {
        ProjectEnvironmentVariable v1 = ProjectEnvironmentVariable.builder()
                .project(sampleProject)
                .key("PORT")
                .value("3000")
                .build();
        envVarRepository.saveAndFlush(v1);

        ProjectEnvironmentVariable v2 = ProjectEnvironmentVariable.builder()
                .project(sampleProject)
                .key("PORT")
                .value("8080")
                .build();

        assertThatThrownBy(() -> envVarRepository.saveAndFlush(v2))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Should allow same key across different projects")
    void shouldAllowSameKeyAcrossDifferentProjects() {
        ProjectEnvironmentVariable v1 = ProjectEnvironmentVariable.builder()
                .project(sampleProject)
                .key("PORT")
                .value("3000")
                .build();
        envVarRepository.saveAndFlush(v1);

        ProjectEnvironmentVariable v2 = ProjectEnvironmentVariable.builder()
                .project(secondProject)
                .key("PORT")
                .value("8080")
                .build();

        ProjectEnvironmentVariable savedV2 = envVarRepository.saveAndFlush(v2);
        assertThat(savedV2.getId()).isNotNull();
        assertThat(savedV2.getProject().getId()).isEqualTo(secondProject.getId());
    }

    @Test
    @DisplayName("Should cascade delete environment variables when parent Project is deleted")
    void shouldCascadeDeleteEnvironmentVariablesWhenProjectIsDeleted() {
        ProjectEnvironmentVariable v1 = ProjectEnvironmentVariable.builder()
                .project(sampleProject)
                .key("API_KEY")
                .value("v1:secret==")
                .isSecret(true)
                .build();
        ProjectEnvironmentVariable v2 = ProjectEnvironmentVariable.builder()
                .project(sampleProject)
                .key("LOG_LEVEL")
                .value("INFO")
                .isSecret(false)
                .build();

        envVarRepository.save(v1);
        envVarRepository.save(v2);
        envVarRepository.flush();

        assertThat(envVarRepository.findAllByProjectId(sampleProject.getId())).hasSize(2);

        // Delete project
        projectRepository.delete(sampleProject);
        projectRepository.flush();

        // Verify all environment variables belonging to the project are deleted
        List<ProjectEnvironmentVariable> remaining = envVarRepository.findAllByProjectId(sampleProject.getId());
        assertThat(remaining).isEmpty();
    }
}

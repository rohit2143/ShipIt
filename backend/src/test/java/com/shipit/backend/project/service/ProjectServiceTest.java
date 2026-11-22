package com.shipit.backend.project.service;

import com.shipit.backend.common.exception.BadRequestException;
import com.shipit.backend.common.exception.ConflictException;
import com.shipit.backend.common.exception.ResourceNotFoundException;
import com.shipit.backend.github.entity.GitHubConnection;
import com.shipit.backend.github.entity.GitHubConnectionStatus;
import com.shipit.backend.github.entity.GitHubRepository;
import com.shipit.backend.github.repository.GitHubRepositoryRepository;
import com.shipit.backend.project.dto.*;
import com.shipit.backend.project.entity.Project;
import com.shipit.backend.project.entity.ProjectEnvironmentVariable;
import com.shipit.backend.project.entity.ProjectStatus;
import com.shipit.backend.project.repository.ProjectRepository;
import com.shipit.backend.user.entity.Role;
import com.shipit.backend.user.entity.User;
import com.shipit.backend.user.entity.UserStatus;
import com.shipit.backend.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private GitHubRepositoryRepository gitHubRepositoryRepository;

    @Mock
    private UserService userService;

    @Mock
    private EncryptionService encryptionService;

    @InjectMocks
    private ProjectService projectService;

    private User testUser;
    private GitHubConnection testConnection;
    private GitHubRepository testRepo;
    private UUID userId;
    private UUID repoId;
    private UUID projectId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        repoId = UUID.randomUUID();
        projectId = UUID.randomUUID();

        testUser = User.builder()
                .id(userId)
                .name("Rohit Kumar")
                .email("rohit@example.com")
                .role(Role.ROLE_USER)
                .status(UserStatus.ACTIVE)
                .emailVerified(true)
                .build();

        testConnection = GitHubConnection.builder()
                .id(UUID.randomUUID())
                .user(testUser)
                .installationId(123456L)
                .status(GitHubConnectionStatus.CONNECTED)
                .build();

        testRepo = GitHubRepository.builder()
                .id(repoId)
                .user(testUser)
                .connection(testConnection)
                .githubRepoId(98765L)
                .name("sample-app")
                .fullName("rohit/sample-app")
                .defaultBranch("main")
                .cloneUrl("https://github.com/rohit/sample-app.git")
                .htmlUrl("https://github.com/rohit/sample-app")
                .isPrivate(false)
                .build();
    }

    @Test
    @DisplayName("createProject - Success with default values and case-insensitive normalization")
    void createProject_Success_DefaultValues() {
        CreateProjectRequest request = CreateProjectRequest.builder()
                .repositoryId(repoId)
                .name("My First App")
                .build();

        when(userService.getById(userId)).thenReturn(testUser);
        when(gitHubRepositoryRepository.findByIdAndUserId(repoId, userId)).thenReturn(Optional.of(testRepo));
        when(projectRepository.existsByUserIdAndNormalizedName(userId, "my first app")).thenReturn(false);
        when(projectRepository.save(any(Project.class))).thenAnswer(invocation -> {
            Project p = invocation.getArgument(0);
            p.setId(projectId);
            return p;
        });

        ProjectResponse response = projectService.createProject(userId, request);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(projectId);
        assertThat(response.getName()).isEqualTo("My First App");
        assertThat(response.getDeploymentBranch()).isEqualTo("main");
        assertThat(response.getRootDirectory()).isEqualTo("/");
        assertThat(response.getPort()).isEqualTo(8080);
        assertThat(response.isAutoDeploy()).isTrue();
        assertThat(response.getStatus()).isEqualTo(ProjectStatus.ACTIVE);
        assertThat(response.getEnvironmentVariablesCount()).isEqualTo(0);
        assertThat(response.getRepositoryName()).isEqualTo("sample-app");

        ArgumentCaptor<Project> projectCaptor = ArgumentCaptor.forClass(Project.class);
        verify(projectRepository).save(projectCaptor.capture());
        Project saved = projectCaptor.getValue();
        assertThat(saved.getNormalizedName()).isEqualTo("my first app");
    }

    @Test
    @DisplayName("createProject - Success with custom values and initial environment variables")
    void createProject_Success_WithInitialEnvVars() {
        CreateProjectRequest request = CreateProjectRequest.builder()
                .repositoryId(repoId)
                .name("Production Backend")
                .description("Production Spring Boot app")
                .deploymentBranch("release/v1")
                .rootDirectory("/app")
                .buildCommand("./mvnw clean package -DskipTests")
                .startCommand("java -jar app.jar")
                .port(8081)
                .autoDeploy(false)
                .environmentVariables(List.of(
                        CreateProjectEnvVarRequest.builder().key("PORT").value("8081").isSecret(false).build(),
                        CreateProjectEnvVarRequest.builder().key("DB_PASS").value("secret123").isSecret(true).build()
                ))
                .build();

        when(userService.getById(userId)).thenReturn(testUser);
        when(gitHubRepositoryRepository.findByIdAndUserId(repoId, userId)).thenReturn(Optional.of(testRepo));
        when(projectRepository.existsByUserIdAndNormalizedName(userId, "production backend")).thenReturn(false);
        when(encryptionService.encrypt(eq(projectId), eq("DB_PASS"), eq("secret123"))).thenReturn("v1:EncryptedDbPass==");
        when(projectRepository.save(any(Project.class))).thenAnswer(invocation -> {
            Project p = invocation.getArgument(0);
            p.setId(projectId);
            return p;
        });

        ProjectResponse response = projectService.createProject(userId, request);

        assertThat(response).isNotNull();
        assertThat(response.getName()).isEqualTo("Production Backend");
        assertThat(response.getDescription()).isEqualTo("Production Spring Boot app");
        assertThat(response.getDeploymentBranch()).isEqualTo("release/v1");
        assertThat(response.getRootDirectory()).isEqualTo("/app");
        assertThat(response.getBuildCommand()).isEqualTo("./mvnw clean package -DskipTests");
        assertThat(response.getStartCommand()).isEqualTo("java -jar app.jar");
        assertThat(response.getPort()).isEqualTo(8081);
        assertThat(response.isAutoDeploy()).isFalse();
        assertThat(response.getEnvironmentVariablesCount()).isEqualTo(2);

        ArgumentCaptor<Project> captor = ArgumentCaptor.forClass(Project.class);
        verify(projectRepository, times(2)).save(captor.capture());
        Project saved = captor.getValue();
        assertThat(saved.getEnvironmentVariables()).hasSize(2);
        assertThat(saved.getEnvironmentVariables().get(0).getKey()).isEqualTo("PORT");
        assertThat(saved.getEnvironmentVariables().get(0).getValue()).isEqualTo("8081");
        assertThat(saved.getEnvironmentVariables().get(0).isSecret()).isFalse();
        assertThat(saved.getEnvironmentVariables().get(1).getKey()).isEqualTo("DB_PASS");
        assertThat(saved.getEnvironmentVariables().get(1).getValue()).isEqualTo("v1:EncryptedDbPass==");
        assertThat(saved.getEnvironmentVariables().get(1).isSecret()).isTrue();
    }

    @Test
    @DisplayName("createProject - Duplicate initial environment variable key throws BadRequestException")
    void createProject_DuplicateEnvVarKey_ThrowsBadRequestException() {
        CreateProjectRequest request = CreateProjectRequest.builder()
                .repositoryId(repoId)
                .name("Valid Project")
                .environmentVariables(List.of(
                        CreateProjectEnvVarRequest.builder().key("DB_HOST").value("localhost").build(),
                        CreateProjectEnvVarRequest.builder().key("DB_HOST").value("127.0.0.1").build()
                ))
                .build();

        when(userService.getById(userId)).thenReturn(testUser);
        when(gitHubRepositoryRepository.findByIdAndUserId(repoId, userId)).thenReturn(Optional.of(testRepo));
        when(projectRepository.existsByUserIdAndNormalizedName(userId, "valid project")).thenReturn(false);

        assertThatThrownBy(() -> projectService.createProject(userId, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Duplicate environment variable key: DB_HOST");

        verify(projectRepository, never()).save(any());
    }

    @Test
    @DisplayName("createProject - Repository not owned by user throws ResourceNotFoundException")
    void createProject_RepoNotOwned_ThrowsResourceNotFoundException() {
        CreateProjectRequest request = CreateProjectRequest.builder()
                .repositoryId(repoId)
                .name("My App")
                .build();

        when(userService.getById(userId)).thenReturn(testUser);
        when(gitHubRepositoryRepository.findByIdAndUserId(repoId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> projectService.createProject(userId, request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Repository not found or access denied");

        verify(projectRepository, never()).save(any());
    }

    @Test
    @DisplayName("createProject - Associated GitHubConnection is not CONNECTED throws BadRequestException")
    void createProject_ConnectionNotConnected_ThrowsBadRequestException() {
        testConnection.setStatus(GitHubConnectionStatus.SUSPENDED);
        CreateProjectRequest request = CreateProjectRequest.builder()
                .repositoryId(repoId)
                .name("My App")
                .build();

        when(userService.getById(userId)).thenReturn(testUser);
        when(gitHubRepositoryRepository.findByIdAndUserId(repoId, userId)).thenReturn(Optional.of(testRepo));

        assertThatThrownBy(() -> projectService.createProject(userId, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Associated GitHub connection is not active or connected");

        verify(projectRepository, never()).save(any());
    }

    @Test
    @DisplayName("createProject - Duplicate project name per user throws ConflictException")
    void createProject_DuplicateName_ThrowsConflictException() {
        CreateProjectRequest request = CreateProjectRequest.builder()
                .repositoryId(repoId)
                .name("My-App")
                .build();

        when(userService.getById(userId)).thenReturn(testUser);
        when(gitHubRepositoryRepository.findByIdAndUserId(repoId, userId)).thenReturn(Optional.of(testRepo));
        when(projectRepository.existsByUserIdAndNormalizedName(userId, "my-app")).thenReturn(true);

        assertThatThrownBy(() -> projectService.createProject(userId, request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Project with name 'My-App' already exists for this user");

        verify(projectRepository, never()).save(any());
    }

    @Test
    @DisplayName("getUserProjects - Returns list of ProjectSummaryResponse")
    void getUserProjects_ReturnsList() {
        Project p1 = Project.builder()
                .id(UUID.randomUUID())
                .user(testUser)
                .repository(testRepo)
                .name("App One")
                .deploymentBranch("main")
                .port(8080)
                .autoDeploy(true)
                .status(ProjectStatus.ACTIVE)
                .build();

        when(projectRepository.findAllByUserId(userId)).thenReturn(List.of(p1));

        List<ProjectSummaryResponse> result = projectService.getUserProjects(userId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("App One");
        assertThat(result.get(0).getRepositoryFullName()).isEqualTo("rohit/sample-app");
    }

    @Test
    @DisplayName("getProject - Returns ProjectResponse when project exists")
    void getProject_Success() {
        Project p1 = Project.builder()
                .id(projectId)
                .user(testUser)
                .repository(testRepo)
                .name("App One")
                .deploymentBranch("main")
                .port(8080)
                .autoDeploy(true)
                .status(ProjectStatus.ACTIVE)
                .environmentVariables(new ArrayList<>())
                .build();

        when(projectRepository.findByIdAndUserId(projectId, userId)).thenReturn(Optional.of(p1));

        ProjectResponse response = projectService.getProject(userId, projectId);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(projectId);
        assertThat(response.getName()).isEqualTo("App One");
    }

    @Test
    @DisplayName("getProject - Throws ResourceNotFoundException when project does not exist")
    void getProject_NotFound_ThrowsResourceNotFoundException() {
        when(projectRepository.findByIdAndUserId(projectId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> projectService.getProject(userId, projectId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Project not found with id: " + projectId);
    }

    @Test
    @DisplayName("updateProject - Updates fields successfully")
    void updateProject_Success() {
        Project existing = Project.builder()
                .id(projectId)
                .user(testUser)
                .repository(testRepo)
                .name("Old Name")
                .normalizedName("old name")
                .deploymentBranch("main")
                .port(8080)
                .autoDeploy(true)
                .status(ProjectStatus.ACTIVE)
                .environmentVariables(new ArrayList<>())
                .build();

        UpdateProjectRequest request = UpdateProjectRequest.builder()
                .name("New Name")
                .description("Updated description")
                .deploymentBranch("develop")
                .rootDirectory("/new-root")
                .buildCommand("mvn clean package")
                .startCommand("java -jar target/app.jar")
                .port(9090)
                .autoDeploy(false)
                .status(ProjectStatus.ARCHIVED)
                .build();

        when(projectRepository.findByIdAndUserId(projectId, userId)).thenReturn(Optional.of(existing));
        when(projectRepository.existsByUserIdAndNormalizedNameAndIdNot(userId, "new name", projectId)).thenReturn(false);
        when(projectRepository.save(any(Project.class))).thenAnswer(i -> i.getArgument(0));

        ProjectResponse response = projectService.updateProject(userId, projectId, request);

        assertThat(response).isNotNull();
        assertThat(response.getName()).isEqualTo("New Name");
        assertThat(response.getDescription()).isEqualTo("Updated description");
        assertThat(response.getDeploymentBranch()).isEqualTo("develop");
        assertThat(response.getRootDirectory()).isEqualTo("/new-root");
        assertThat(response.getBuildCommand()).isEqualTo("mvn clean package");
        assertThat(response.getStartCommand()).isEqualTo("java -jar target/app.jar");
        assertThat(response.getPort()).isEqualTo(9090);
        assertThat(response.isAutoDeploy()).isFalse();
        assertThat(response.getStatus()).isEqualTo(ProjectStatus.ARCHIVED);
    }

    @Test
    @DisplayName("updateProject - Duplicate name on another project throws ConflictException")
    void updateProject_DuplicateName_ThrowsConflictException() {
        Project existing = Project.builder()
                .id(projectId)
                .user(testUser)
                .repository(testRepo)
                .name("Old Name")
                .normalizedName("old name")
                .build();

        UpdateProjectRequest request = UpdateProjectRequest.builder()
                .name("Existing App")
                .build();

        when(projectRepository.findByIdAndUserId(projectId, userId)).thenReturn(Optional.of(existing));
        when(projectRepository.existsByUserIdAndNormalizedNameAndIdNot(userId, "existing app", projectId)).thenReturn(true);

        assertThatThrownBy(() -> projectService.updateProject(userId, projectId, request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Project with name 'Existing App' already exists for this user");

        verify(projectRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateProject - Same name on current project proceeds without conflict")
    void updateProject_SameName_Success() {
        Project existing = Project.builder()
                .id(projectId)
                .user(testUser)
                .repository(testRepo)
                .name("Current Name")
                .normalizedName("current name")
                .port(8080)
                .environmentVariables(new ArrayList<>())
                .build();

        UpdateProjectRequest request = UpdateProjectRequest.builder()
                .name("Current Name")
                .port(8082)
                .build();

        when(projectRepository.findByIdAndUserId(projectId, userId)).thenReturn(Optional.of(existing));
        when(projectRepository.existsByUserIdAndNormalizedNameAndIdNot(userId, "current name", projectId)).thenReturn(false);
        when(projectRepository.save(any(Project.class))).thenAnswer(i -> i.getArgument(0));

        ProjectResponse response = projectService.updateProject(userId, projectId, request);

        assertThat(response).isNotNull();
        assertThat(response.getPort()).isEqualTo(8082);
    }

    @Test
    @DisplayName("deleteProject - Success deletes project")
    void deleteProject_Success() {
        Project existing = Project.builder()
                .id(projectId)
                .user(testUser)
                .repository(testRepo)
                .name("To Delete")
                .build();

        when(projectRepository.findByIdAndUserId(projectId, userId)).thenReturn(Optional.of(existing));

        projectService.deleteProject(userId, projectId);

        verify(projectRepository).delete(existing);
    }

    @Test
    @DisplayName("deleteProject - Project not found throws ResourceNotFoundException")
    void deleteProject_NotFound_ThrowsResourceNotFoundException() {
        when(projectRepository.findByIdAndUserId(projectId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> projectService.deleteProject(userId, projectId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Project not found with id: " + projectId);

        verify(projectRepository, never()).delete(any());
    }
}

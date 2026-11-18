package com.shipit.backend.project.service;

import com.shipit.backend.common.exception.BadRequestException;
import com.shipit.backend.common.exception.ConflictException;
import com.shipit.backend.common.exception.ResourceNotFoundException;
import com.shipit.backend.github.entity.GitHubConnectionStatus;
import com.shipit.backend.github.entity.GitHubRepository;
import com.shipit.backend.github.repository.GitHubRepositoryRepository;
import com.shipit.backend.project.dto.*;
import com.shipit.backend.project.entity.Project;
import com.shipit.backend.project.entity.ProjectEnvironmentVariable;
import com.shipit.backend.project.entity.ProjectStatus;
import com.shipit.backend.project.repository.ProjectRepository;
import com.shipit.backend.user.entity.User;
import com.shipit.backend.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final GitHubRepositoryRepository gitHubRepositoryRepository;
    private final UserService userService;
    private final EncryptionService encryptionService;

    @Transactional
    public ProjectResponse createProject(UUID userId, CreateProjectRequest request) {
        log.info("Creating project '{}' for user: {}", request.getName(), userId);

        User user = userService.getById(userId);

        GitHubRepository repository = gitHubRepositoryRepository.findByIdAndUserId(request.getRepositoryId(), userId)
                .orElseThrow(() -> new ResourceNotFoundException("Repository not found or access denied"));

        if (repository.getConnection() == null || repository.getConnection().getStatus() != GitHubConnectionStatus.CONNECTED) {
            throw new BadRequestException("Associated GitHub connection is not active or connected", "GITHUB_CONNECTION_NOT_ACTIVE");
        }

        String trimmedName = request.getName().trim();
        String normalizedName = trimmedName.toLowerCase(Locale.ROOT);

        if (projectRepository.existsByUserIdAndNormalizedName(userId, normalizedName)) {
            throw new ConflictException("Project with name '" + trimmedName + "' already exists for this user", "PROJECT_NAME_EXISTS");
        }

        String deploymentBranch = (request.getDeploymentBranch() != null && !request.getDeploymentBranch().isBlank())
                ? request.getDeploymentBranch().trim()
                : (repository.getDefaultBranch() != null ? repository.getDefaultBranch() : "main");

        String rootDirectory = (request.getRootDirectory() != null && !request.getRootDirectory().isBlank())
                ? request.getRootDirectory().trim()
                : "/";

        Integer port = request.getPort() != null ? request.getPort() : 8080;
        boolean autoDeploy = request.getAutoDeploy() != null ? request.getAutoDeploy() : true;

        Project project = Project.builder()
                .user(user)
                .repository(repository)
                .name(trimmedName)
                .normalizedName(normalizedName)
                .description(request.getDescription() != null ? request.getDescription().trim() : null)
                .deploymentBranch(deploymentBranch)
                .rootDirectory(rootDirectory)
                .buildCommand(request.getBuildCommand() != null ? request.getBuildCommand().trim() : null)
                .startCommand(request.getStartCommand() != null ? request.getStartCommand().trim() : null)
                .port(port)
                .autoDeploy(autoDeploy)
                .status(ProjectStatus.ACTIVE)
                .build();

        if (request.getEnvironmentVariables() != null && !request.getEnvironmentVariables().isEmpty()) {
            Set<String> seenKeys = new HashSet<>();
            for (CreateProjectEnvVarRequest envReq : request.getEnvironmentVariables()) {
                if (!seenKeys.add(envReq.getKey())) {
                    throw new BadRequestException("Duplicate environment variable key: " + envReq.getKey(), "DUPLICATE_ENV_VAR_KEY");
                }
            }
        }

        Project savedProject = projectRepository.save(project);

        if (request.getEnvironmentVariables() != null && !request.getEnvironmentVariables().isEmpty()) {
            for (CreateProjectEnvVarRequest envReq : request.getEnvironmentVariables()) {
                boolean isSecret = Boolean.TRUE.equals(envReq.getIsSecret());
                String storageValue = isSecret
                        ? encryptionService.encrypt(savedProject.getId(), envReq.getKey(), envReq.getValue())
                        : envReq.getValue();

                ProjectEnvironmentVariable envVar = ProjectEnvironmentVariable.builder()
                        .project(savedProject)
                        .key(envReq.getKey())
                        .value(storageValue)
                        .isSecret(isSecret)
                        .build();
                savedProject.getEnvironmentVariables().add(envVar);
            }
            savedProject = projectRepository.save(savedProject);
        }

        log.info("Successfully created project id: {} with {} initial environment variables",
                savedProject.getId(), savedProject.getEnvironmentVariables().size());

        return ProjectResponse.fromEntity(savedProject, savedProject.getEnvironmentVariables().size());
    }

    @Transactional(readOnly = true)
    public List<ProjectSummaryResponse> getUserProjects(UUID userId) {
        log.debug("Fetching all projects for user: {}", userId);
        return projectRepository.findAllByUserId(userId).stream()
                .map(ProjectSummaryResponse::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProjectResponse getProject(UUID userId, UUID projectId) {
        log.debug("Fetching project id: {} for user: {}", projectId, userId);
        Project project = projectRepository.findByIdAndUserId(projectId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found with id: " + projectId));

        return ProjectResponse.fromEntity(project, project.getEnvironmentVariables().size());
    }

    @Transactional(readOnly = true)
    public Project getProjectEntity(UUID userId, UUID projectId) {
        return projectRepository.findByIdAndUserId(projectId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found with id: " + projectId));
    }

    @Transactional(readOnly = true)
    public Project getProjectEntityById(UUID projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found with id: " + projectId));
    }

    @Transactional
    public ProjectResponse updateProject(UUID userId, UUID projectId, UpdateProjectRequest request) {
        log.info("Updating project id: {} for user: {}", projectId, userId);

        Project project = projectRepository.findByIdAndUserId(projectId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found with id: " + projectId));

        if (request.getName() != null && !request.getName().isBlank()) {
            String trimmedName = request.getName().trim();
            String normalizedName = trimmedName.toLowerCase(Locale.ROOT);
            if (projectRepository.existsByUserIdAndNormalizedNameAndIdNot(userId, normalizedName, projectId)) {
                throw new ConflictException("Project with name '" + trimmedName + "' already exists for this user", "PROJECT_NAME_EXISTS");
            }
            project.setName(trimmedName);
            project.setNormalizedName(normalizedName);
        }

        if (request.getDescription() != null) {
            project.setDescription(request.getDescription().trim());
        }

        if (request.getDeploymentBranch() != null && !request.getDeploymentBranch().isBlank()) {
            project.setDeploymentBranch(request.getDeploymentBranch().trim());
        }

        if (request.getRootDirectory() != null && !request.getRootDirectory().isBlank()) {
            project.setRootDirectory(request.getRootDirectory().trim());
        }

        if (request.getBuildCommand() != null) {
            project.setBuildCommand(request.getBuildCommand().trim());
        }

        if (request.getStartCommand() != null) {
            project.setStartCommand(request.getStartCommand().trim());
        }

        if (request.getPort() != null) {
            project.setPort(request.getPort());
        }

        if (request.getAutoDeploy() != null) {
            project.setAutoDeploy(request.getAutoDeploy());
        }

        if (request.getStatus() != null) {
            project.setStatus(request.getStatus());
        }

        Project updatedProject = projectRepository.save(project);
        log.info("Successfully updated project id: {}", updatedProject.getId());

        return ProjectResponse.fromEntity(updatedProject, updatedProject.getEnvironmentVariables().size());
    }

    @Transactional
    public void deleteProject(UUID userId, UUID projectId) {
        log.info("Deleting project id: {} for user: {}", projectId, userId);

        Project project = projectRepository.findByIdAndUserId(projectId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found with id: " + projectId));

        projectRepository.delete(project);
        log.info("Successfully deleted project id: {} for user: {}", projectId, userId);
    }
}

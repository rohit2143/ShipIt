package com.shipit.backend.job.worker;

import com.shipit.backend.build.config.BuildProperties;
import com.shipit.backend.build.detector.ProjectDetector;
import com.shipit.backend.build.generator.DockerfileGenerator;
import com.shipit.backend.build.logging.BuildLogBuffer;
import com.shipit.backend.build.logging.LogSource;
import com.shipit.backend.build.model.BuildPlan;
import com.shipit.backend.build.model.BuildResult;
import com.shipit.backend.build.service.BuildLogService;
import com.shipit.backend.build.service.DockerBuildService;
import com.shipit.backend.build.service.GitCloneService;
import com.shipit.backend.build.workspace.WorkspaceManager;
import com.shipit.backend.container.config.ContainerProperties;
import com.shipit.backend.container.health.ContainerHealthChecker;
import com.shipit.backend.container.model.ContainerConfig;
import com.shipit.backend.container.port.PortAllocator;
import com.shipit.backend.container.service.DockerContainerService;
import com.shipit.backend.deployment.entity.Deployment;
import com.shipit.backend.deployment.entity.DeploymentStatus;
import com.shipit.backend.deployment.repository.DeploymentRepository;
import com.shipit.backend.deployment.service.DeploymentService;
import com.shipit.backend.job.entity.DeploymentJob;
import com.shipit.backend.job.service.DeploymentJobService;
import com.shipit.backend.log.entity.DeploymentStage;
import com.shipit.backend.log.service.DeploymentLogService;
import com.shipit.backend.project.entity.Project;
import com.shipit.backend.project.service.ProjectEnvironmentVariableService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultDeploymentTaskHandler implements DeploymentTaskHandler {

    private final DeploymentRepository deploymentRepository;
    private final DeploymentService deploymentService;
    private final DeploymentJobService jobService;
    private final GitCloneService gitCloneService;
    private final ProjectDetector projectDetector;
    private final DockerfileGenerator dockerfileGenerator;
    private final DockerBuildService dockerBuildService;
    private final WorkspaceManager workspaceManager;
    private final BuildLogService buildLogService;
    private final BuildProperties buildProperties;
    private final DockerContainerService dockerContainerService;
    private final PortAllocator portAllocator;
    private final ContainerHealthChecker containerHealthChecker;
    private final ProjectEnvironmentVariableService projectEnvironmentVariableService;
    private final ContainerProperties containerProperties;
    private final DeploymentLogService deploymentLogService;

    @Override
    public void handleDeployment(DeploymentJob job) {
        if (job == null || job.getDeployment() == null) {
            log.warn("Job or associated deployment is null; aborting execution");
            return;
        }

        UUID deploymentId = job.getDeployment().getId();
        log.info("Starting execution for deployment id: {} (Job ID: {})", deploymentId, job.getId());

        Deployment deployment = deploymentRepository.findByIdWithDetails(deploymentId)
                .orElseGet(() -> deploymentRepository.findById(deploymentId).orElse(null));
        if (deployment == null) {
            log.warn("Deployment id: {} no longer exists; aborting execution", deploymentId);
            return;
        }

        // If deployment was cancelled while queued, do not proceed
        if (deployment.getStatus() == DeploymentStatus.CANCELLED) {
            log.info("Deployment id: {} was cancelled before execution started; skipping", deploymentId);
            return;
        }

        Project project = deployment.getProject();

        AtomicReference<DeploymentStage> currentStage = new AtomicReference<>(DeploymentStage.SYSTEM);
        Map<String, String> projectEnvVars = new HashMap<>();
        Set<String> sensitiveValues = new HashSet<>();
        if (project != null && project.getId() != null) {
            try {
                projectEnvVars.putAll(projectEnvironmentVariableService
                        .getDecryptedEnvironmentVariablesForDeployment(project.getId()));
                sensitiveValues.addAll(projectEnvVars.values());
            } catch (Exception ex) {
                log.warn("Failed to pre-fetch sensitive env vars for log redaction: {}", ex.getMessage());
            }
        }

        BuildLogBuffer logBuffer = new BuildLogBuffer(
                buildProperties.getMaxLogLines(),
                buildProperties.getMaxLineLength(),
                (source, line) -> {
                    try {
                        deploymentLogService.appendLog(deploymentId, currentStage.get(), source, line, sensitiveValues);
                    } catch (Exception e) {
                        log.warn("Error streaming log line for deployment {}: {}", deploymentId, e.getMessage());
                    }
                }
        );
        String commitRef = deployment.getCommitSha() != null ? deployment.getCommitSha() : deployment.getCommitBranch();
        logBuffer.append(LogSource.SYSTEM, String.format("=== Starting build execution for deployment %s (%s) ===",
                deploymentId, commitRef));

        Path workspaceRoot = null;
        Integer allocatedHostPort = null;
        String containerId = null;

        try {
            // Step 1: CLONING
            checkCancelled(deploymentId);
            currentStage.set(DeploymentStage.CLONING);
            deployment = deploymentService.transitionStatus(deploymentId, DeploymentStatus.CLONING, null);
            deployment.setProject(project);
            deploymentLogService.recordStatusChange(deploymentId, DeploymentStatus.CLONING, "Repository cloning started");
            jobService.recordHeartbeat(job.getId());
            log.debug("Deployment {} entered CLONING", deploymentId);
            logBuffer.append(LogSource.SYSTEM, "Cloning repository branch: " + deployment.getCommitBranch());

            workspaceRoot = gitCloneService.cloneRepository(deployment, logBuffer);
            jobService.recordHeartbeat(job.getId());

            // Step 2: DETECTING
            checkCancelled(deploymentId);
            currentStage.set(DeploymentStage.DETECTING);
            deployment = deploymentService.transitionStatus(deploymentId, DeploymentStatus.DETECTING, null);
            deployment.setProject(project);
            deploymentLogService.recordStatusChange(deploymentId, DeploymentStatus.DETECTING, "Project detection started");
            jobService.recordHeartbeat(job.getId());
            log.debug("Deployment {} entered DETECTING", deploymentId);
            logBuffer.append(LogSource.SYSTEM, "Detecting project layout and generating build specification...");

            BuildPlan buildPlan = projectDetector.detect(workspaceRoot, deployment, logBuffer);
            Path dockerfilePath = Path.of(buildPlan.getDockerfilePath());
            Path appRoot = dockerfilePath.getParent() != null ? dockerfilePath.getParent() : workspaceRoot;

            dockerfileGenerator.generateDockerfile(appRoot, buildPlan, logBuffer);
            jobService.recordHeartbeat(job.getId());

            // Step 3: BUILDING
            checkCancelled(deploymentId);
            currentStage.set(DeploymentStage.BUILDING);
            deployment = deploymentService.transitionStatus(deploymentId, DeploymentStatus.BUILDING, null);
            deployment.setProject(project);
            deploymentLogService.recordStatusChange(deploymentId, DeploymentStatus.BUILDING, "Container image build started");
            jobService.recordHeartbeat(job.getId());
            log.debug("Deployment {} entered BUILDING", deploymentId);
            logBuffer.append(LogSource.SYSTEM, "Executing Docker container build...");

            BuildResult buildResult = dockerBuildService.buildImage(appRoot, buildPlan, logBuffer);
            jobService.recordHeartbeat(job.getId());

            deployment.setDockerImageTag(buildResult.getImageTag());
            deployment = deploymentRepository.save(deployment);
            deployment.setProject(project);
            logBuffer.append(LogSource.SYSTEM, String.format("Successfully built image: %s (Duration: %ds)",
                    buildResult.getImageTag(), buildResult.getDurationSeconds()));

            // Step 4: STARTING
            checkCancelled(deploymentId);
            currentStage.set(DeploymentStage.STARTING);
            deployment = deploymentService.transitionStatus(deploymentId, DeploymentStatus.STARTING, null);
            deployment.setProject(project);
            deploymentLogService.recordStatusChange(deploymentId, DeploymentStatus.STARTING, "Container startup started");
            jobService.recordHeartbeat(job.getId());
            log.debug("Deployment {} entered STARTING", deploymentId);
            logBuffer.append(LogSource.SYSTEM, "Allocating host port and starting container...");

            allocatedHostPort = portAllocator.allocatePort();
            logBuffer.append(LogSource.SYSTEM, "Allocated host port: " + allocatedHostPort);

            Map<String, String> envVars = new HashMap<>(projectEnvVars);
            int containerPort = (project != null && project.getPort() != null) ? project.getPort() : 8080;
            envVars.put("PORT", String.valueOf(containerPort));
            envVars.put("SHIPIT_DEPLOYMENT_ID", deploymentId.toString());
            if (project != null && project.getId() != null) {
                envVars.put("SHIPIT_PROJECT_ID", project.getId().toString());
            }

            String normalizedName = (project != null && project.getNormalizedName() != null && !project.getNormalizedName().isBlank())
                    ? project.getNormalizedName()
                    : "app";
            String shortId = deploymentId.toString().substring(0, 8);
            String containerName = "shipit-" + normalizedName + "-" + shortId;

            ContainerConfig containerConfig = ContainerConfig.builder()
                    .deploymentId(deploymentId)
                    .projectId(project != null ? project.getId() : null)
                    .containerName(containerName)
                    .imageTag(deployment.getDockerImageTag())
                    .hostPort(allocatedHostPort)
                    .containerPort(containerPort)
                    .environmentVariables(envVars)
                    .memoryLimit(containerProperties.getMemoryLimit())
                    .cpuLimit(containerProperties.getCpuLimit())
                    .restartPolicy(containerProperties.getRestartPolicy())
                    .build();

            containerId = dockerContainerService.runContainer(containerConfig, logBuffer);
            deployment.setContainerId(containerId);
            deployment.setContainerPort(allocatedHostPort);
            deployment = deploymentRepository.save(deployment);
            deployment.setProject(project);
            jobService.recordHeartbeat(job.getId());
            logBuffer.append(LogSource.SYSTEM, String.format("Container %s successfully started on host port %d",
                    containerId, allocatedHostPort));

            // Step 5: HEALTH_CHECKING
            checkCancelled(deploymentId);
            currentStage.set(DeploymentStage.HEALTH_CHECKING);
            deployment = deploymentService.transitionStatus(deploymentId, DeploymentStatus.HEALTH_CHECKING, null);
            deployment.setProject(project);
            deploymentLogService.recordStatusChange(deploymentId, DeploymentStatus.HEALTH_CHECKING, "Health check verification started");
            jobService.recordHeartbeat(job.getId());
            log.debug("Deployment {} entered HEALTH_CHECKING", deploymentId);
            logBuffer.append(LogSource.SYSTEM, "Running health check verification on port " + allocatedHostPort + "...");

            containerHealthChecker.verifyHealth(
                    containerId,
                    allocatedHostPort,
                    containerProperties.getHealthCheckPath(),
                    logBuffer
            );
            jobService.recordHeartbeat(job.getId());
            logBuffer.append(LogSource.SYSTEM, "Health check probe passed successfully. Container is healthy.");

            // Step 6: SUCCESS
            checkCancelled(deploymentId);
            currentStage.set(DeploymentStage.CONTAINER);
            deployment = deploymentService.transitionStatus(deploymentId, DeploymentStatus.SUCCESS, null);
            deployment.setProject(project);
            deploymentLogService.recordStatusChange(deploymentId, DeploymentStatus.SUCCESS, "Deployment completed successfully");
            logBuffer.append(LogSource.SYSTEM, "Deployment " + deploymentId + " finished successfully.");
            log.info("Deployment {} completed successfully", deploymentId);

            // Clean up any older active containers for this project
            cleanupPreviousContainers(project, deploymentId);

        } catch (DeploymentCancelledException e) {
            log.warn("Deployment {} execution halted: {}", deploymentId, e.getMessage());
            logBuffer.append(LogSource.SYSTEM, "Execution halted: " + e.getMessage());
            rollbackContainerAndPort(deploymentId, containerId, allocatedHostPort);
            deploymentLogService.recordStatusChange(deploymentId, DeploymentStatus.CANCELLED, e.getMessage());
        } catch (Exception e) {
            log.error("Deployment {} failed during execution: {}", deploymentId, e.getMessage(), e);
            logBuffer.append(LogSource.SYSTEM, "Execution failed: " + e.getMessage());
            rollbackContainerAndPort(deploymentId, containerId, allocatedHostPort);
            deploymentService.transitionStatus(deploymentId, DeploymentStatus.FAILED, e.getMessage());
            deploymentLogService.recordStatusChange(deploymentId, DeploymentStatus.FAILED, e.getMessage());
            throw e;
        } finally {
            try {
                buildLogService.storeLogs(deploymentId, logBuffer.getAllLogsAsString());
            } catch (Exception ex) {
                log.error("Failed to store build logs for deployment {}", deploymentId, ex);
            }
            try {
                workspaceManager.deleteWorkspace(deploymentId);
            } catch (Exception ex) {
                log.warn("Failed to delete scratch workspace for deployment {}: {}", deploymentId, ex.getMessage());
            }
        }
    }

    private void rollbackContainerAndPort(UUID deploymentId, String containerId, Integer allocatedPort) {
        if (containerId != null) {
            try {
                log.info("Cleaning up container {} after deployment failure or cancellation", containerId);
                dockerContainerService.removeContainer(containerId, true);
            } catch (Exception ex) {
                log.warn("Failed to remove container {}: {}", containerId, ex.getMessage());
            }
        }
        if (allocatedPort != null) {
            try {
                log.info("Releasing allocated port {} after deployment failure or cancellation", allocatedPort);
                portAllocator.releasePort(allocatedPort);
            } catch (Exception ex) {
                log.warn("Failed to release port {}: {}", allocatedPort, ex.getMessage());
            }
        }
        if (deploymentId != null) {
            try {
                deploymentRepository.findById(deploymentId).ifPresent(d -> {
                    d.setContainerId(null);
                    d.setContainerPort(null);
                    deploymentRepository.save(d);
                });
            } catch (Exception ex) {
                log.warn("Failed to clear container metadata on failed deployment {}: {}", deploymentId, ex.getMessage());
            }
        }
    }

    private void cleanupPreviousContainers(Project project, UUID currentDeploymentId) {
        if (project == null || project.getId() == null) {
            return;
        }
        try {
            List<Deployment> previousDeployments = deploymentRepository.findByProjectIdAndContainerIdIsNotNullAndIdNot(
                    project.getId(), currentDeploymentId
            );
            for (Deployment prev : previousDeployments) {
                String oldContainerId = prev.getContainerId();
                Integer oldPort = prev.getContainerPort();
                log.info("Stopping and cleaning up previous container {} (port: {}) for project {}",
                        oldContainerId, oldPort, project.getId());
                try {
                    dockerContainerService.stopContainer(oldContainerId, containerProperties.getStopTimeoutSeconds());
                } catch (Exception e) {
                    log.warn("Failed to stop previous container {}: {}", oldContainerId, e.getMessage());
                }
                try {
                    dockerContainerService.removeContainer(oldContainerId, true);
                } catch (Exception e) {
                    log.warn("Failed to remove previous container {}: {}", oldContainerId, e.getMessage());
                }
                if (oldPort != null) {
                    try {
                        portAllocator.releasePort(oldPort);
                    } catch (Exception e) {
                        log.warn("Failed to release port {}: {}", oldPort, e.getMessage());
                    }
                }
                prev.setContainerId(null);
                prev.setContainerPort(null);
                deploymentRepository.save(prev);
            }
        } catch (Exception e) {
            log.warn("Failed to cleanup previous containers for project {}: {}", project.getId(), e.getMessage());
        }
    }

    private void checkCancelled(UUID deploymentId) {
        Deployment current = deploymentRepository.findById(deploymentId).orElse(null);
        if (current != null && current.getStatus() == DeploymentStatus.CANCELLED) {
            throw new DeploymentCancelledException("Deployment " + deploymentId + " was cancelled");
        }
    }

    private static class DeploymentCancelledException extends RuntimeException {
        public DeploymentCancelledException(String message) {
            super(message);
        }
    }
}

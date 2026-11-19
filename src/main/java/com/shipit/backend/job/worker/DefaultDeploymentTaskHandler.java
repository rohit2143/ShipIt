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
import com.shipit.backend.deployment.entity.Deployment;
import com.shipit.backend.deployment.entity.DeploymentStatus;
import com.shipit.backend.deployment.repository.DeploymentRepository;
import com.shipit.backend.deployment.service.DeploymentService;
import com.shipit.backend.job.entity.DeploymentJob;
import com.shipit.backend.job.service.DeploymentJobService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.UUID;

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

        BuildLogBuffer logBuffer = new BuildLogBuffer(
                buildProperties.getMaxLogLines(),
                buildProperties.getMaxLineLength()
        );
        String commitRef = deployment.getCommitSha() != null ? deployment.getCommitSha() : deployment.getCommitBranch();
        logBuffer.append(LogSource.SYSTEM, String.format("=== Starting build execution for deployment %s (%s) ===",
                deploymentId, commitRef));

        Path workspaceRoot = null;
        try {
            // Step 1: CLONING
            checkCancelled(deploymentId);
            deployment = deploymentService.transitionStatus(deploymentId, DeploymentStatus.CLONING, null);
            jobService.recordHeartbeat(job.getId());
            log.debug("Deployment {} entered CLONING", deploymentId);
            logBuffer.append(LogSource.SYSTEM, "Cloning repository branch: " + deployment.getCommitBranch());

            workspaceRoot = gitCloneService.cloneRepository(deployment, logBuffer);
            jobService.recordHeartbeat(job.getId());

            // Step 2: DETECTING
            checkCancelled(deploymentId);
            deployment = deploymentService.transitionStatus(deploymentId, DeploymentStatus.DETECTING, null);
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
            deployment = deploymentService.transitionStatus(deploymentId, DeploymentStatus.BUILDING, null);
            jobService.recordHeartbeat(job.getId());
            log.debug("Deployment {} entered BUILDING", deploymentId);
            logBuffer.append(LogSource.SYSTEM, "Executing Docker container build...");

            BuildResult buildResult = dockerBuildService.buildImage(appRoot, buildPlan, logBuffer);
            jobService.recordHeartbeat(job.getId());

            deployment.setDockerImageTag(buildResult.getImageTag());
            deployment = deploymentRepository.save(deployment);
            logBuffer.append(LogSource.SYSTEM, String.format("Successfully built image: %s (Duration: %ds)",
                    buildResult.getImageTag(), buildResult.getDurationSeconds()));

            // Step 4: STARTING (Module 7 placeholder)
            checkCancelled(deploymentId);
            deployment = deploymentService.transitionStatus(deploymentId, DeploymentStatus.STARTING, null);
            jobService.recordHeartbeat(job.getId());
            log.debug("Deployment {} entered STARTING", deploymentId);

            // Step 5: HEALTH_CHECKING (Module 7/9 placeholder)
            checkCancelled(deploymentId);
            deployment = deploymentService.transitionStatus(deploymentId, DeploymentStatus.HEALTH_CHECKING, null);
            jobService.recordHeartbeat(job.getId());
            log.debug("Deployment {} entered HEALTH_CHECKING", deploymentId);

            // Step 6: SUCCESS
            checkCancelled(deploymentId);
            deployment = deploymentService.transitionStatus(deploymentId, DeploymentStatus.SUCCESS, null);
            logBuffer.append(LogSource.SYSTEM, "Deployment " + deploymentId + " finished successfully.");
            log.info("Deployment {} completed successfully", deploymentId);

        } catch (DeploymentCancelledException e) {
            // Thrown if deployment was cancelled
            log.warn("Deployment {} execution halted: {}", deploymentId, e.getMessage());
            logBuffer.append(LogSource.SYSTEM, "Execution halted: " + e.getMessage());
        } catch (Exception e) {
            log.error("Deployment {} failed during execution: {}", deploymentId, e.getMessage(), e);
            logBuffer.append(LogSource.SYSTEM, "Execution failed: " + e.getMessage());
            deploymentService.transitionStatus(deploymentId, DeploymentStatus.FAILED, e.getMessage());
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

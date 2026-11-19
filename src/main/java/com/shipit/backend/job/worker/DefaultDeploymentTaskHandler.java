package com.shipit.backend.job.worker;

import com.shipit.backend.deployment.entity.Deployment;
import com.shipit.backend.deployment.entity.DeploymentStatus;
import com.shipit.backend.deployment.repository.DeploymentRepository;
import com.shipit.backend.deployment.service.DeploymentService;
import com.shipit.backend.job.entity.DeploymentJob;
import com.shipit.backend.job.service.DeploymentJobService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultDeploymentTaskHandler implements DeploymentTaskHandler {

    private final DeploymentRepository deploymentRepository;
    private final DeploymentService deploymentService;
    private final DeploymentJobService jobService;

    @Override
    public void handleDeployment(DeploymentJob job) {
        if (job == null || job.getDeployment() == null) {
            log.warn("Job or associated deployment is null; aborting execution");
            return;
        }

        UUID deploymentId = job.getDeployment().getId();
        log.info("Starting execution for deployment id: {} (Job ID: {})", deploymentId, job.getId());

        Deployment deployment = deploymentRepository.findById(deploymentId).orElse(null);
        if (deployment == null) {
            log.warn("Deployment id: {} no longer exists; aborting execution", deploymentId);
            return;
        }

        // If deployment was cancelled while queued, do not proceed
        if (deployment.getStatus() == DeploymentStatus.CANCELLED) {
            log.info("Deployment id: {} was cancelled before execution started; skipping", deploymentId);
            return;
        }

        try {
            // Step 1: CLONING
            checkCancelled(deploymentId);
            deploymentService.transitionStatus(deploymentId, DeploymentStatus.CLONING, null);
            jobService.recordHeartbeat(job.getId());
            log.debug("Deployment {} entered CLONING", deploymentId);

            // Step 2: DETECTING
            checkCancelled(deploymentId);
            deploymentService.transitionStatus(deploymentId, DeploymentStatus.DETECTING, null);
            jobService.recordHeartbeat(job.getId());
            log.debug("Deployment {} entered DETECTING", deploymentId);

            // Step 3: BUILDING
            checkCancelled(deploymentId);
            deploymentService.transitionStatus(deploymentId, DeploymentStatus.BUILDING, null);
            jobService.recordHeartbeat(job.getId());
            log.debug("Deployment {} entered BUILDING", deploymentId);

            // Step 4: STARTING
            checkCancelled(deploymentId);
            deploymentService.transitionStatus(deploymentId, DeploymentStatus.STARTING, null);
            jobService.recordHeartbeat(job.getId());
            log.debug("Deployment {} entered STARTING", deploymentId);

            // Step 5: HEALTH_CHECKING
            checkCancelled(deploymentId);
            deploymentService.transitionStatus(deploymentId, DeploymentStatus.HEALTH_CHECKING, null);
            jobService.recordHeartbeat(job.getId());
            log.debug("Deployment {} entered HEALTH_CHECKING", deploymentId);

            // Step 6: SUCCESS
            checkCancelled(deploymentId);
            deploymentService.transitionStatus(deploymentId, DeploymentStatus.SUCCESS, null);
            log.info("Deployment {} completed successfully", deploymentId);

        } catch (IllegalStateException e) {
            // Thrown if deployment was cancelled
            log.warn("Deployment {} execution halted: {}", deploymentId, e.getMessage());
        } catch (Exception e) {
            log.error("Deployment {} failed during execution: {}", deploymentId, e.getMessage(), e);
            deploymentService.transitionStatus(deploymentId, DeploymentStatus.FAILED, e.getMessage());
            throw e;
        }
    }

    private void checkCancelled(UUID deploymentId) {
        Deployment current = deploymentRepository.findById(deploymentId).orElse(null);
        if (current != null && current.getStatus() == DeploymentStatus.CANCELLED) {
            throw new IllegalStateException("Deployment " + deploymentId + " was cancelled");
        }
    }
}

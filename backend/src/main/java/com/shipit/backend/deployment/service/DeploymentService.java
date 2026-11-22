package com.shipit.backend.deployment.service;

import com.shipit.backend.common.exception.BadRequestException;
import com.shipit.backend.common.exception.ConflictException;
import com.shipit.backend.common.exception.ResourceNotFoundException;
import com.shipit.backend.deployment.dto.DeploymentResponse;
import com.shipit.backend.deployment.dto.DeploymentSummaryResponse;
import com.shipit.backend.deployment.dto.TriggerDeploymentRequest;
import com.shipit.backend.deployment.entity.Deployment;
import com.shipit.backend.deployment.entity.DeploymentStatus;
import com.shipit.backend.deployment.entity.DeploymentTrigger;
import com.shipit.backend.deployment.repository.DeploymentRepository;
import com.shipit.backend.job.service.DeploymentJobService;
import com.shipit.backend.project.entity.Project;
import com.shipit.backend.project.entity.ProjectStatus;
import com.shipit.backend.project.repository.ProjectRepository;
import com.shipit.backend.user.entity.User;
import com.shipit.backend.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeploymentService {

    private final DeploymentRepository deploymentRepository;
    private final ProjectRepository projectRepository;
    private final UserService userService;
    private final DeploymentJobService deploymentJobService;

    @Transactional
    public DeploymentResponse triggerManualDeployment(UUID userId, UUID projectId, TriggerDeploymentRequest request) {
        log.info("Triggering manual deployment for project: {} by user: {}", projectId, userId);

        User user = userService.getById(userId);
        Project project = projectRepository.findByIdAndUserId(projectId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found with id: " + projectId));

        if (project.getStatus() != ProjectStatus.ACTIVE) {
            throw new BadRequestException("Cannot deploy project with status: " + project.getStatus(), "PROJECT_NOT_ACTIVE");
        }

        String branch = (request != null && request.getBranch() != null && !request.getBranch().isBlank())
                ? request.getBranch().trim()
                : project.getDeploymentBranch();

        if (branch == null || branch.isBlank()) {
            branch = "main";
        }

        Deployment deployment = Deployment.builder()
                .project(project)
                .status(DeploymentStatus.QUEUED)
                .trigger(DeploymentTrigger.MANUAL)
                .commitBranch(branch)
                .commitSha(request != null && request.getCommitSha() != null ? request.getCommitSha().trim() : null)
                .commitMessage(request != null && request.getCommitMessage() != null ? request.getCommitMessage().trim() : null)
                .commitAuthor(user.getName() != null ? user.getName() : user.getEmail())
                .queuedAt(Instant.now())
                .build();

        Deployment saved = deploymentRepository.save(deployment);
        deploymentJobService.createJob(saved, 0);
        log.info("Successfully queued manual deployment id: {} and background job for project: {}", saved.getId(), projectId);

        return DeploymentResponse.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public Page<DeploymentSummaryResponse> getProjectDeployments(UUID userId, UUID projectId, Pageable pageable) {
        log.debug("Fetching deployments for project: {} user: {}", projectId, userId);

        if (projectRepository.findByIdAndUserId(projectId, userId).isEmpty()) {
            throw new ResourceNotFoundException("Project not found with id: " + projectId);
        }

        return deploymentRepository.findAllByProjectId(projectId, pageable)
                .map(DeploymentSummaryResponse::fromEntity);
    }

    @Transactional(readOnly = true)
    public DeploymentResponse getDeployment(UUID userId, UUID deploymentId) {
        log.debug("Fetching deployment id: {} user: {}", deploymentId, userId);

        Deployment deployment = deploymentRepository.findByIdAndUserId(deploymentId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Deployment not found with id: " + deploymentId));

        return DeploymentResponse.fromEntity(deployment);
    }

    @Transactional
    public DeploymentResponse cancelDeployment(UUID userId, UUID deploymentId) {
        log.info("Attempting to cancel deployment id: {} by user: {}", deploymentId, userId);

        Deployment deployment = deploymentRepository.findByIdAndUserId(deploymentId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Deployment not found with id: " + deploymentId));

        if (deployment.getStatus().isTerminal()) {
            throw new ConflictException("Deployment is already in terminal state: " + deployment.getStatus(), "DEPLOYMENT_ALREADY_TERMINAL");
        }

        if (deployment.getStatus() != DeploymentStatus.QUEUED && deployment.getStatus() != DeploymentStatus.CLONING) {
            throw new ConflictException("Cannot cancel deployment in " + deployment.getStatus() + " status", "CANNOT_CANCEL_DEPLOYMENT");
        }

        deployment.cancel();
        Deployment saved = deploymentRepository.save(deployment);

        deploymentJobService.getLatestJobForDeployment(saved.getId())
                .ifPresent(job -> {
                    if (!job.getStatus().isTerminal()) {
                        deploymentJobService.cancelJob(job.getId());
                    }
                });

        log.info("Successfully cancelled deployment id: {} and associated background job", saved.getId());

        return DeploymentResponse.fromEntity(saved);
    }

    @Transactional
    public DeploymentResponse retryDeployment(UUID userId, UUID deploymentId) {
        log.info("Attempting to retry deployment id: {} by user: {}", deploymentId, userId);

        User user = userService.getById(userId);
        Deployment original = deploymentRepository.findByIdAndUserId(deploymentId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Deployment not found with id: " + deploymentId));

        if (original.getStatus() != DeploymentStatus.FAILED && original.getStatus() != DeploymentStatus.CANCELLED) {
            throw new BadRequestException("Only failed or cancelled deployments can be retried. Current status: " + original.getStatus(), "DEPLOYMENT_NOT_RETRYABLE");
        }

        if (original.getProject().getStatus() != ProjectStatus.ACTIVE) {
            throw new BadRequestException("Cannot retry deployment for project with status: " + original.getProject().getStatus(), "PROJECT_NOT_ACTIVE");
        }

        String retryMessage = original.getCommitMessage() != null
                ? "Retry: " + original.getCommitMessage()
                : "Retry of deployment " + original.getId();

        if (retryMessage.length() > 1000) {
            retryMessage = retryMessage.substring(0, 1000);
        }

        Deployment retry = Deployment.builder()
                .project(original.getProject())
                .status(DeploymentStatus.QUEUED)
                .trigger(DeploymentTrigger.RETRY)
                .commitBranch(original.getCommitBranch())
                .commitSha(original.getCommitSha())
                .commitMessage(retryMessage)
                .commitAuthor(user.getName() != null ? user.getName() : user.getEmail())
                .queuedAt(Instant.now())
                .build();

        Deployment saved = deploymentRepository.save(retry);
        deploymentJobService.createJob(saved, 0);
        log.info("Successfully queued retry deployment id: {} and background job for original deployment id: {}", saved.getId(), original.getId());

        return DeploymentResponse.fromEntity(saved);
    }

    @Transactional
    public Deployment transitionStatus(UUID deploymentId, DeploymentStatus newStatus, String errorMessage) {
        log.debug("Transitioning deployment id: {} to status: {}", deploymentId, newStatus);

        Deployment deployment = deploymentRepository.findByIdWithDetails(deploymentId)
                .orElseGet(() -> deploymentRepository.findById(deploymentId)
                        .orElseThrow(() -> new ResourceNotFoundException("Deployment not found with id: " + deploymentId)));

        if (newStatus == DeploymentStatus.FAILED) {
            deployment.markFailed(errorMessage);
        } else {
            deployment.transitionTo(newStatus);
        }

        return deploymentRepository.save(deployment);
    }
}

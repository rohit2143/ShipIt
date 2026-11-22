package com.shipit.backend.deployment.listener;

import com.shipit.backend.deployment.entity.Deployment;
import com.shipit.backend.deployment.entity.DeploymentStatus;
import com.shipit.backend.deployment.entity.DeploymentTrigger;
import com.shipit.backend.deployment.repository.DeploymentRepository;
import com.shipit.backend.job.service.DeploymentJobService;
import com.shipit.backend.project.entity.Project;
import com.shipit.backend.project.entity.ProjectStatus;
import com.shipit.backend.project.event.ProjectDeploymentTriggerEvent;
import com.shipit.backend.project.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class DeploymentTriggerEventListener {

    private final DeploymentRepository deploymentRepository;
    private final ProjectRepository projectRepository;
    private final DeploymentJobService deploymentJobService;

    @EventListener
    @Transactional
    public void onProjectDeploymentTrigger(ProjectDeploymentTriggerEvent event) {
        if (event == null || event.getProjectId() == null) {
            log.warn("Received null ProjectDeploymentTriggerEvent or null projectId; skipping");
            return;
        }

        log.info("Received ProjectDeploymentTriggerEvent for project id: '{}' on branch '{}', commit: {}",
                event.getProjectId(), event.getBranch(), event.getCommitSha());

        Optional<Project> projectOptional = projectRepository.findById(event.getProjectId());
        if (projectOptional.isEmpty()) {
            log.warn("Project with id: {} not found; cannot create webhook deployment", event.getProjectId());
            return;
        }

        Project project = projectOptional.get();
        if (project.getStatus() != ProjectStatus.ACTIVE) {
            log.info("Project '{}' (id: {}) is not ACTIVE (status: {}); ignoring webhook deployment trigger",
                    project.getName(), project.getId(), project.getStatus());
            return;
        }

        String commitMessage = event.getCommitMessage();
        if (commitMessage != null && commitMessage.length() > 1000) {
            commitMessage = commitMessage.substring(0, 1000);
        }

        String commitSha = event.getCommitSha();
        if (commitSha != null && commitSha.length() > 40) {
            commitSha = commitSha.substring(0, 40);
        }

        String commitAuthor = (event.getPusherName() != null && !event.getPusherName().isBlank())
                ? event.getPusherName().trim()
                : ((event.getPusherEmail() != null && !event.getPusherEmail().isBlank())
                        ? event.getPusherEmail().trim()
                        : "GitHub Webhook");

        if (commitAuthor.length() > 100) {
            commitAuthor = commitAuthor.substring(0, 100);
        }

        String branch = (event.getBranch() != null && !event.getBranch().isBlank())
                ? event.getBranch().trim()
                : (project.getDeploymentBranch() != null ? project.getDeploymentBranch() : "main");

        Deployment deployment = Deployment.builder()
                .project(project)
                .status(DeploymentStatus.QUEUED)
                .trigger(DeploymentTrigger.WEBHOOK)
                .commitBranch(branch)
                .commitSha(commitSha)
                .commitMessage(commitMessage)
                .commitAuthor(commitAuthor)
                .queuedAt(event.getTriggeredAt() != null ? event.getTriggeredAt() : Instant.now())
                .build();

        Deployment saved = deploymentRepository.save(deployment);
        deploymentJobService.createJob(saved, 0);
        log.info("Successfully queued auto-deployment id: {} and background job for project '{}' (id: {}) via webhook push event",
                saved.getId(), project.getName(), project.getId());
    }
}

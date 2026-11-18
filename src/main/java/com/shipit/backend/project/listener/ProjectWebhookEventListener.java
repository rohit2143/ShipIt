package com.shipit.backend.project.listener;

import com.shipit.backend.github.event.GitHubPushReceivedEvent;
import com.shipit.backend.project.entity.Project;
import com.shipit.backend.project.entity.ProjectStatus;
import com.shipit.backend.project.event.ProjectDeploymentTriggerEvent;
import com.shipit.backend.project.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProjectWebhookEventListener {

    private final ProjectRepository projectRepository;
    private final ApplicationEventPublisher eventPublisher;

    @EventListener
    @Transactional(readOnly = true)
    public void onGitHubPushReceived(GitHubPushReceivedEvent event) {
        if (event == null || event.getRepositoryId() == null || event.getBranch() == null) {
            log.warn("Received invalid GitHubPushReceivedEvent: event or key fields are null");
            return;
        }

        log.info("Processing GitHub push event for repo ID: '{}' ({}) on branch '{}', commit: {}",
                event.getRepositoryId(), event.getRepoFullName(), event.getBranch(), event.getCommitSha());

        List<Project> eligibleProjects = projectRepository.findActiveAutoDeployProjects(
                event.getRepositoryId(),
                event.getBranch(),
                ProjectStatus.ACTIVE
        );

        if (eligibleProjects.isEmpty()) {
            log.info("No active auto-deploy projects found matching repository ID {} and branch '{}'",
                    event.getRepositoryId(), event.getBranch());
            return;
        }

        log.info("Found {} eligible project(s) for auto-deployment on push to '{}/{}'",
                eligibleProjects.size(), event.getRepoFullName(), event.getBranch());

        for (Project project : eligibleProjects) {
            log.info("Triggering auto-deployment for project '{}' (ID: {}) commit: {}",
                    project.getName(), project.getId(), event.getCommitSha());

            ProjectDeploymentTriggerEvent triggerEvent = ProjectDeploymentTriggerEvent.builder()
                    .projectId(project.getId())
                    .projectName(project.getName())
                    .repositoryId(event.getRepositoryId())
                    .repoFullName(event.getRepoFullName())
                    .branch(event.getBranch())
                    .commitSha(event.getCommitSha())
                    .commitMessage(event.getCommitMessage())
                    .pusherName(event.getPusherName())
                    .pusherEmail(event.getPusherEmail())
                    .triggeredAt(Instant.now())
                    .build();

            eventPublisher.publishEvent(triggerEvent);
        }
    }
}

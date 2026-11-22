package com.shipit.backend.deployment.dto;

import com.shipit.backend.deployment.entity.Deployment;
import com.shipit.backend.deployment.entity.DeploymentStatus;
import com.shipit.backend.deployment.entity.DeploymentTrigger;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeploymentResponse {

    private UUID id;
    private UUID projectId;
    private String projectName;
    private DeploymentStatus status;
    private DeploymentTrigger trigger;
    private String commitSha;
    private String commitMessage;
    private String commitBranch;
    private String commitAuthor;
    private Instant queuedAt;
    private Instant startedAt;
    private Instant finishedAt;
    private Long durationSeconds;
    private String errorMessage;
    private String dockerImageTag;
    private String containerId;
    private Integer containerPort;
    private Instant createdAt;
    private Instant updatedAt;

    public static DeploymentResponse fromEntity(Deployment deployment) {
        if (deployment == null) {
            return null;
        }

        Long duration = null;
        if (deployment.getStartedAt() != null) {
            Instant end = deployment.getFinishedAt() != null ? deployment.getFinishedAt() : Instant.now();
            duration = Duration.between(deployment.getStartedAt(), end).toSeconds();
        }

        return DeploymentResponse.builder()
                .id(deployment.getId())
                .projectId(deployment.getProject() != null ? deployment.getProject().getId() : null)
                .projectName(deployment.getProject() != null ? deployment.getProject().getName() : null)
                .status(deployment.getStatus())
                .trigger(deployment.getTrigger())
                .commitSha(deployment.getCommitSha())
                .commitMessage(deployment.getCommitMessage())
                .commitBranch(deployment.getCommitBranch())
                .commitAuthor(deployment.getCommitAuthor())
                .queuedAt(deployment.getQueuedAt())
                .startedAt(deployment.getStartedAt())
                .finishedAt(deployment.getFinishedAt())
                .durationSeconds(duration)
                .errorMessage(deployment.getErrorMessage())
                .dockerImageTag(deployment.getDockerImageTag())
                .containerId(deployment.getContainerId())
                .containerPort(deployment.getContainerPort())
                .createdAt(deployment.getCreatedAt())
                .updatedAt(deployment.getUpdatedAt())
                .build();
    }
}

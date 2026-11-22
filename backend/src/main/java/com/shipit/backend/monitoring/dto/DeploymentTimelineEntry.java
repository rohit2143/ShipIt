package com.shipit.backend.monitoring.dto;

import com.shipit.backend.deployment.entity.Deployment;
import com.shipit.backend.deployment.entity.DeploymentStatus;
import com.shipit.backend.deployment.entity.DeploymentTrigger;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Historical record representation of a single deployment execution.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeploymentTimelineEntry {

    private UUID deploymentId;
    private DeploymentStatus status;
    private DeploymentTrigger trigger;
    private String commitSha;
    private String commitBranch;
    private String commitMessage;
    private String commitAuthor;
    private String dockerImageTag;
    private Long durationSeconds;
    private Instant queuedAt;
    private Instant startedAt;
    private Instant finishedAt;
    private String errorMessage;
    private Integer containerPort;

    /**
     * Converts a Deployment entity to a timeline DTO, calculating duration.
     */
    public static DeploymentTimelineEntry fromEntity(Deployment deployment) {
        if (deployment == null) {
            return null;
        }

        Long duration = null;
        if (deployment.getStartedAt() != null && deployment.getFinishedAt() != null) {
            duration = Math.max(0L, Duration.between(deployment.getStartedAt(), deployment.getFinishedAt()).toSeconds());
        }

        return DeploymentTimelineEntry.builder()
                .deploymentId(deployment.getId())
                .status(deployment.getStatus())
                .trigger(deployment.getTrigger())
                .commitSha(deployment.getCommitSha())
                .commitBranch(deployment.getCommitBranch())
                .commitMessage(deployment.getCommitMessage())
                .commitAuthor(deployment.getCommitAuthor())
                .dockerImageTag(deployment.getDockerImageTag())
                .durationSeconds(duration)
                .queuedAt(deployment.getQueuedAt())
                .startedAt(deployment.getStartedAt())
                .finishedAt(deployment.getFinishedAt())
                .errorMessage(deployment.getErrorMessage())
                .containerPort(deployment.getContainerPort())
                .build();
    }
}

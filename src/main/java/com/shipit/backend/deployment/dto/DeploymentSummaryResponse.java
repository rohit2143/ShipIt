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
public class DeploymentSummaryResponse {

    private UUID id;
    private UUID projectId;
    private DeploymentStatus status;
    private DeploymentTrigger trigger;
    private String commitSha;
    private String commitBranch;
    private String commitAuthor;
    private Instant queuedAt;
    private Instant startedAt;
    private Instant finishedAt;
    private Long durationSeconds;
    private Instant createdAt;

    public static DeploymentSummaryResponse fromEntity(Deployment deployment) {
        if (deployment == null) {
            return null;
        }

        Long duration = null;
        if (deployment.getStartedAt() != null) {
            Instant end = deployment.getFinishedAt() != null ? deployment.getFinishedAt() : Instant.now();
            duration = Duration.between(deployment.getStartedAt(), end).toSeconds();
        }

        return DeploymentSummaryResponse.builder()
                .id(deployment.getId())
                .projectId(deployment.getProject() != null ? deployment.getProject().getId() : null)
                .status(deployment.getStatus())
                .trigger(deployment.getTrigger())
                .commitSha(deployment.getCommitSha())
                .commitBranch(deployment.getCommitBranch())
                .commitAuthor(deployment.getCommitAuthor())
                .queuedAt(deployment.getQueuedAt())
                .startedAt(deployment.getStartedAt())
                .finishedAt(deployment.getFinishedAt())
                .durationSeconds(duration)
                .createdAt(deployment.getCreatedAt())
                .build();
    }
}

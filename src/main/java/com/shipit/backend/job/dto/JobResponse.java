package com.shipit.backend.job.dto;

import com.shipit.backend.job.entity.DeploymentJob;
import com.shipit.backend.job.entity.JobStatus;
import com.shipit.backend.job.entity.JobType;
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
public class JobResponse {

    private UUID id;
    private UUID deploymentId;
    private UUID projectId;
    private JobType type;
    private JobStatus status;
    private int priority;
    private int retryCount;
    private int maxRetries;
    private String claimedBy;
    private Instant queuedAt;
    private Instant claimedAt;
    private Instant heartbeatAt;
    private Instant finishedAt;
    private Long durationSeconds;
    private String errorMessage;
    private Instant createdAt;
    private Instant updatedAt;

    public static JobResponse fromEntity(DeploymentJob job) {
        if (job == null) {
            return null;
        }

        Long duration = null;
        if (job.getClaimedAt() != null) {
            Instant end = job.getFinishedAt() != null ? job.getFinishedAt() : Instant.now();
            duration = Duration.between(job.getClaimedAt(), end).toSeconds();
        }

        UUID deploymentId = job.getDeployment() != null ? job.getDeployment().getId() : null;
        UUID projectId = (job.getDeployment() != null && job.getDeployment().getProject() != null)
                ? job.getDeployment().getProject().getId()
                : null;

        return JobResponse.builder()
                .id(job.getId())
                .deploymentId(deploymentId)
                .projectId(projectId)
                .type(job.getType())
                .status(job.getStatus())
                .priority(job.getPriority())
                .retryCount(job.getRetryCount())
                .maxRetries(job.getMaxRetries())
                .claimedBy(job.getClaimedBy())
                .queuedAt(job.getQueuedAt())
                .claimedAt(job.getClaimedAt())
                .heartbeatAt(job.getHeartbeatAt())
                .finishedAt(job.getFinishedAt())
                .durationSeconds(duration)
                .errorMessage(job.getErrorMessage())
                .createdAt(job.getCreatedAt())
                .updatedAt(job.getUpdatedAt())
                .build();
    }
}

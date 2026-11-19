package com.shipit.backend.job.dto;

import com.shipit.backend.job.entity.DeploymentJob;
import com.shipit.backend.job.entity.JobStatus;
import com.shipit.backend.job.entity.JobType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobSummaryResponse {

    private UUID id;
    private UUID deploymentId;
    private JobType type;
    private JobStatus status;
    private int priority;
    private int retryCount;
    private String claimedBy;
    private Instant queuedAt;
    private Instant finishedAt;

    public static JobSummaryResponse fromEntity(DeploymentJob job) {
        if (job == null) {
            return null;
        }

        UUID deploymentId = job.getDeployment() != null ? job.getDeployment().getId() : null;

        return JobSummaryResponse.builder()
                .id(job.getId())
                .deploymentId(deploymentId)
                .type(job.getType())
                .status(job.getStatus())
                .priority(job.getPriority())
                .retryCount(job.getRetryCount())
                .claimedBy(job.getClaimedBy())
                .queuedAt(job.getQueuedAt())
                .finishedAt(job.getFinishedAt())
                .build();
    }
}

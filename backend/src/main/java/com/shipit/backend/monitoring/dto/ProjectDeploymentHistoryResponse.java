package com.shipit.backend.monitoring.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/**
 * Summary analytics and chronological deployment timeline for a project.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectDeploymentHistoryResponse {

    private UUID projectId;
    private String projectName;

    private long totalDeployments;
    private long successfulDeployments;
    private long failedDeployments;
    private long cancelledDeployments;

    private double successRatePercent;
    private double averageDurationSeconds;

    private DeploymentTimelineEntry activeDeployment;
    private List<DeploymentTimelineEntry> timeline;

    private int page;
    private int size;
    private boolean hasMore;
}

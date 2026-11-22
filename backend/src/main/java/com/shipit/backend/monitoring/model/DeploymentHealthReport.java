package com.shipit.backend.monitoring.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Health and availability status report for a deployed application container.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeploymentHealthReport {

    private UUID deploymentId;
    private UUID projectId;
    private String containerId;
    private Integer hostPort;

    @Builder.Default
    private AvailabilityStatus availability = AvailabilityStatus.UNKNOWN;

    private Integer httpStatus;
    private boolean httpProbePassed;
    private boolean containerRunning;
    private int consecutiveFailures;

    private ContainerMetrics metrics;

    @Builder.Default
    private Instant lastCheckedAt = Instant.now();

    private String details;
}

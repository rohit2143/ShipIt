package com.shipit.backend.container.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Inspection snapshot representing the state and metadata of a Docker container.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContainerInfo {

    private String containerId;
    private String containerName;
    private UUID deploymentId;
    private String imageTag;
    private ContainerStatus status;
    private boolean running;
    private int hostPort;
    private int containerPort;
    private Instant startedAt;
    private Instant finishedAt;
    private Integer exitCode;
    private String healthStatus;
    private String ipAddress;

    public boolean isRunning() {
        return status != null && status.isActive();
    }
}

package com.shipit.backend.monitoring.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Snapshot of runtime hardware utilization and lifecycle metrics for a container.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContainerMetrics {

    private String containerId;
    private String status;
    private boolean running;

    @Builder.Default
    private double cpuPercent = 0.0;

    @Builder.Default
    private String memoryUsageFormatted = "0B / 0B";

    @Builder.Default
    private long memoryUsageBytes = 0L;

    @Builder.Default
    private long memoryLimitBytes = 0L;

    @Builder.Default
    private double memoryPercent = 0.0;

    @Builder.Default
    private int pidsCount = 0;

    @Builder.Default
    private int restartCount = 0;

    private Instant startedAt;

    @Builder.Default
    private long uptimeSeconds = 0L;

    @Builder.Default
    private Instant collectedAt = Instant.now();

    /**
     * Creates an empty/zeroed metric snapshot for a non-running or missing container.
     */
    public static ContainerMetrics stopped(String containerId) {
        return ContainerMetrics.builder()
                .containerId(containerId)
                .status("stopped")
                .running(false)
                .cpuPercent(0.0)
                .memoryUsageFormatted("0B / 0B")
                .memoryUsageBytes(0L)
                .memoryLimitBytes(0L)
                .memoryPercent(0.0)
                .pidsCount(0)
                .restartCount(0)
                .uptimeSeconds(0L)
                .collectedAt(Instant.now())
                .build();
    }
}

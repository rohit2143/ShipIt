package com.shipit.backend.job.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "shipit.worker")
public class DeploymentWorkerProperties {

    /**
     * Whether the background worker polling loop is enabled.
     * Default: true.
     */
    private boolean enabled = true;

    /**
     * Unique identifier for this worker node.
     * If blank, an identifier will be auto-generated on startup.
     */
    private String workerId;

    /**
     * Fixed delay in milliseconds between polling attempts for new pending jobs.
     */
    private long pollIntervalMs = 2000L;

    /**
     * Interval in milliseconds for sending heartbeats on actively running jobs.
     */
    private long heartbeatIntervalMs = 10000L;

    /**
     * Seconds without a heartbeat before a running job is declared stale and eligible for recovery.
     */
    private long staleThresholdSeconds = 180L;

    /**
     * Fixed delay in milliseconds between stale job recovery scans.
     */
    private long recoveryIntervalMs = 60000L;
}

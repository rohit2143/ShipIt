package com.shipit.backend.monitoring.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for Module 9 Health Monitoring and Metrics.
 */
@Data
@Component
@ConfigurationProperties(prefix = "shipit.monitoring")
public class MonitoringProperties {

    /**
     * Polling interval in seconds for periodic active container health probes.
     */
    private int healthCheckIntervalSeconds = 30;

    /**
     * HTTP socket connection and read timeout in seconds for container health checks.
     */
    private int healthCheckTimeoutSeconds = 5;

    /**
     * Number of consecutive health check failures before marking deployment UNHEALTHY/DEGRADED.
     */
    private int maxUnhealthyThreshold = 3;

    /**
     * Timeout in seconds for executing Docker stats CLI commands.
     */
    private int statsTimeoutSeconds = 5;

    /**
     * Number of days to retain historical deployment and metrics records.
     */
    private int historyRetentionDays = 30;
}

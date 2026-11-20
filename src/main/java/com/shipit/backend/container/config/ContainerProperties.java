package com.shipit.backend.container.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for container runtime limits, host port allocation,
 * and container health checking.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "shipit.container")
public class ContainerProperties {

    /**
     * Minimum port for dynamic host container port allocation range.
     */
    private int minPort = 10000;

    /**
     * Maximum port for dynamic host container port allocation range.
     */
    private int maxPort = 20000;

    /**
     * Default container memory limit (e.g. 512m).
     */
    private String memoryLimit = "512m";

    /**
     * Default container CPU quota limit (e.g. 0.5).
     */
    private String cpuLimit = "0.5";

    /**
     * Maximum seconds to wait for a newly started container to pass HTTP health checks.
     */
    private int healthCheckTimeoutSeconds = 60;

    /**
     * Interval in seconds between consecutive health check probes.
     */
    private int healthCheckIntervalSeconds = 2;

    /**
     * Default HTTP health check path.
     */
    private String healthCheckPath = "/";

    /**
     * Seconds to wait for graceful container shutdown before SIGKILL.
     */
    private int stopTimeoutSeconds = 10;

    /**
     * Default restart policy for deployed containers.
     */
    private String restartPolicy = "unless-stopped";
}

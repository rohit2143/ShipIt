package com.shipit.backend.log.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for Module 8 Deployment Logs and Streaming.
 */
@Data
@Component
@ConfigurationProperties(prefix = "shipit.logs")
public class LogProperties {

    /**
     * Maximum character length permitted for an individual log line before truncation.
     */
    private int maxLineLength = 2048;

    /**
     * Maximum number of log lines persisted in the database per deployment.
     */
    private int maxPersistedLinesPerDeployment = 10000;

    /**
     * Batch size for writing log records to PostgreSQL.
     */
    private int batchSize = 50;

    /**
     * Heartbeat interval in seconds for active Server-Sent Events (SSE) connections.
     */
    private int streamHeartbeatSeconds = 15;

    /**
     * Maximum number of historical log lines to replay to newly connected real-time clients.
     */
    private int maxReplayLines = 1000;
}

package com.shipit.backend.build.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "shipit.build")
public class BuildProperties {

    /**
     * Base directory on the host where temporary build scratch workspaces are cloned and executed.
     */
    private String workspaceBaseDir = "/tmp/shipit-builds";

    /**
     * Maximum allowed timeout for an individual build process in seconds (default: 600s / 10m).
     */
    private long timeoutSeconds = 600;

    /**
     * Maximum number of log lines retained per build log buffer to protect memory footprint.
     */
    private int maxLogLines = 5000;

    /**
     * Maximum character length per individual log line to prevent memory bloat on giant lines.
     */
    private int maxLineLength = 1024;

    /**
     * Default multi-stage builder base image for Maven / Java builds.
     */
    private String defaultJdkImage = "eclipse-temurin:21-jdk-alpine";

    /**
     * Default minimal runtime image for Java applications.
     */
    private String defaultJreImage = "eclipse-temurin:21-jre-alpine";
}

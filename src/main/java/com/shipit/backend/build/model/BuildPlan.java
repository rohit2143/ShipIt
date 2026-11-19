package com.shipit.backend.build.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Encapsulates the execution strategy, container metadata, and target environment
 * discovered during project analysis for a specific deployment build.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BuildPlan {

    private UUID projectId;
    private UUID deploymentId;
    private ProjectType projectType;
    private String rootDirectory;
    private String dockerfilePath;
    private boolean customDockerfile;
    private String buildCommand;
    private int port;
    private String imageTag;
    private String jdkImage;
    private String jreImage;

    @Builder.Default
    private Instant createdAt = Instant.now();

    /**
     * Validates that the build plan contains all mandatory fields required for execution.
     */
    public boolean isValid() {
        return projectId != null
                && deploymentId != null
                && projectType != null
                && projectType != ProjectType.UNKNOWN
                && imageTag != null
                && !imageTag.isBlank()
                && port > 0
                && port <= 65535;
    }
}

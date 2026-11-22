package com.shipit.backend.container.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Encapsulates the execution parameters required to instantiate and run
 * an application container.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContainerConfig {

    private UUID deploymentId;
    private UUID projectId;
    private String containerName;
    private String imageTag;
    private int hostPort;
    private int containerPort;

    @Builder.Default
    private Map<String, String> environmentVariables = new HashMap<>();

    @Builder.Default
    private String memoryLimit = "512m";

    @Builder.Default
    private String cpuLimit = "0.5";

    @Builder.Default
    private String restartPolicy = "unless-stopped";

    public boolean isValid() {
        return deploymentId != null
                && projectId != null
                && containerName != null && !containerName.isBlank()
                && imageTag != null && !imageTag.isBlank()
                && hostPort > 0 && hostPort <= 65535
                && containerPort > 0 && containerPort <= 65535;
    }
}

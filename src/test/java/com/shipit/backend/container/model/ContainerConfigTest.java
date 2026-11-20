package com.shipit.backend.container.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ContainerConfigTest {

    @Test
    @DisplayName("isValid returns true when all required fields are correctly populated")
    void validConfig() {
        ContainerConfig config = ContainerConfig.builder()
                .deploymentId(UUID.randomUUID())
                .projectId(UUID.randomUUID())
                .containerName("shipit-demo-app-abc1234")
                .imageTag("shipit/demo-app:abc1234")
                .hostPort(10050)
                .containerPort(8080)
                .environmentVariables(Map.of("PORT", "8080"))
                .memoryLimit("512m")
                .cpuLimit("0.5")
                .restartPolicy("unless-stopped")
                .build();

        assertThat(config.isValid()).isTrue();
    }

    @Test
    @DisplayName("isValid returns false when mandatory fields are missing or invalid")
    void invalidConfigs() {
        UUID depId = UUID.randomUUID();
        UUID projId = UUID.randomUUID();

        // Missing deploymentId
        assertThat(ContainerConfig.builder()
                .projectId(projId)
                .containerName("c1")
                .imageTag("img:tag")
                .hostPort(10000)
                .containerPort(8080)
                .build().isValid()).isFalse();

        // Missing projectId
        assertThat(ContainerConfig.builder()
                .deploymentId(depId)
                .containerName("c1")
                .imageTag("img:tag")
                .hostPort(10000)
                .containerPort(8080)
                .build().isValid()).isFalse();

        // Blank containerName
        assertThat(ContainerConfig.builder()
                .deploymentId(depId)
                .projectId(projId)
                .containerName("  ")
                .imageTag("img:tag")
                .hostPort(10000)
                .containerPort(8080)
                .build().isValid()).isFalse();

        // Blank imageTag
        assertThat(ContainerConfig.builder()
                .deploymentId(depId)
                .projectId(projId)
                .containerName("c1")
                .imageTag("")
                .hostPort(10000)
                .containerPort(8080)
                .build().isValid()).isFalse();

        // Invalid hostPort
        assertThat(ContainerConfig.builder()
                .deploymentId(depId)
                .projectId(projId)
                .containerName("c1")
                .imageTag("img:tag")
                .hostPort(0)
                .containerPort(8080)
                .build().isValid()).isFalse();

        assertThat(ContainerConfig.builder()
                .deploymentId(depId)
                .projectId(projId)
                .containerName("c1")
                .imageTag("img:tag")
                .hostPort(70000)
                .containerPort(8080)
                .build().isValid()).isFalse();

        // Invalid containerPort
        assertThat(ContainerConfig.builder()
                .deploymentId(depId)
                .projectId(projId)
                .containerName("c1")
                .imageTag("img:tag")
                .hostPort(10000)
                .containerPort(-1)
                .build().isValid()).isFalse();
    }

    @Test
    @DisplayName("Default values for resource limits and restart policy are set correctly")
    void defaultValues() {
        ContainerConfig config = new ContainerConfig();

        assertThat(config.getMemoryLimit()).isEqualTo("512m");
        assertThat(config.getCpuLimit()).isEqualTo("0.5");
        assertThat(config.getRestartPolicy()).isEqualTo("unless-stopped");
        assertThat(config.getEnvironmentVariables()).isNotNull();
    }
}

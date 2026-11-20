package com.shipit.backend.container.service;

import com.shipit.backend.build.logging.BuildLogBuffer;
import com.shipit.backend.build.logging.LogSource;
import com.shipit.backend.build.process.ProcessExecutor;
import com.shipit.backend.build.process.ProcessResult;
import com.shipit.backend.container.config.ContainerProperties;
import com.shipit.backend.container.exception.ContainerException;
import com.shipit.backend.container.model.ContainerConfig;
import com.shipit.backend.container.model.ContainerInfo;
import com.shipit.backend.container.model.ContainerStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DockerContainerServiceTest {

    @Mock
    private ProcessExecutor processExecutor;

    @Mock
    private ContainerProperties containerProperties;

    @InjectMocks
    private DockerContainerService containerService;

    @Captor
    private ArgumentCaptor<List<String>> commandCaptor;

    @Captor
    private ArgumentCaptor<Set<String>> sensitiveCaptor;

    private ContainerConfig sampleConfig;
    private UUID deploymentId;
    private UUID projectId;

    @BeforeEach
    void setUp() {
        deploymentId = UUID.randomUUID();
        projectId = UUID.randomUUID();

        sampleConfig = ContainerConfig.builder()
                .deploymentId(deploymentId)
                .projectId(projectId)
                .containerName("shipit-demo-abc1234")
                .imageTag("shipit/demo:abc1234")
                .hostPort(10050)
                .containerPort(8080)
                .environmentVariables(Map.of("DB_PASS", "supersecret123", "APP_ENV", "prod"))
                .memoryLimit("512m")
                .cpuLimit("0.5")
                .restartPolicy("unless-stopped")
                .build();
    }

    @Test
    @DisplayName("runContainer - Successfully launches container and returns container ID")
    void runContainer_Success() {
        BuildLogBuffer logBuffer = new BuildLogBuffer(100, 1024);
        String expectedContainerId = "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2";

        when(processExecutor.execute(commandCaptor.capture(), any(), any(), anyLong(), sensitiveCaptor.capture()))
                .thenAnswer(inv -> {
                    BuildLogBuffer buf = inv.getArgument(2);
                    if (buf != null) {
                        buf.append(LogSource.STDOUT, expectedContainerId);
                    }
                    return new ProcessResult(0, false);
                });

        String containerId = containerService.runContainer(sampleConfig, logBuffer);

        assertThat(containerId).isEqualTo(expectedContainerId);

        List<String> cmd = commandCaptor.getValue();
        assertThat(cmd).contains("docker", "run", "-d", "--name", "shipit-demo-abc1234");
        assertThat(cmd).contains("-p", "10050:8080");
        assertThat(cmd).contains("-m", "512m");
        assertThat(cmd).contains("--cpus", "0.5");
        assertThat(cmd).contains("--restart", "unless-stopped");
        assertThat(cmd).contains("shipit/demo:abc1234");

        // Sensitive env vars captured for masking
        Set<String> sensitive = sensitiveCaptor.getValue();
        assertThat(sensitive).contains("supersecret123");
    }

    @Test
    @DisplayName("runContainer - Null or invalid configuration throws IllegalArgumentException")
    void runContainer_InvalidConfig() {
        BuildLogBuffer logBuffer = new BuildLogBuffer();

        assertThatThrownBy(() -> containerService.runContainer(null, logBuffer))
                .isInstanceOf(IllegalArgumentException.class);

        ContainerConfig invalidConfig = ContainerConfig.builder().build();
        assertThatThrownBy(() -> containerService.runContainer(invalidConfig, logBuffer))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("runContainer - Process timeout throws ContainerException")
    void runContainer_Timeout() {
        BuildLogBuffer logBuffer = new BuildLogBuffer();
        when(processExecutor.execute(any(), any(), any(), anyLong(), any()))
                .thenReturn(new ProcessResult(-1, true));

        assertThatThrownBy(() -> containerService.runContainer(sampleConfig, logBuffer))
                .isInstanceOf(ContainerException.class)
                .hasMessageContaining("Timed out");
    }

    @Test
    @DisplayName("runContainer - Non-zero exit code throws ContainerException")
    void runContainer_FailedExit() {
        BuildLogBuffer logBuffer = new BuildLogBuffer();
        when(processExecutor.execute(any(), any(), any(), anyLong(), any()))
                .thenReturn(new ProcessResult(125, false));

        assertThatThrownBy(() -> containerService.runContainer(sampleConfig, logBuffer))
                .isInstanceOf(ContainerException.class)
                .hasMessageContaining("exit code 125");
    }

    @Test
    @DisplayName("stopContainer - Stops container with configured timeout")
    void stopContainer_Success() {
        when(containerProperties.getStopTimeoutSeconds()).thenReturn(10);
        when(processExecutor.execute(commandCaptor.capture(), any(), any(), anyLong(), any()))
                .thenReturn(new ProcessResult(0, false));

        boolean stopped = containerService.stopContainer("c12345", null);

        assertThat(stopped).isTrue();
        assertThat(commandCaptor.getValue()).containsExactly("docker", "stop", "-t", "10", "c12345");
    }

    @Test
    @DisplayName("stopContainer - Blank container ID returns false without executing")
    void stopContainer_BlankId() {
        assertThat(containerService.stopContainer(null, null)).isFalse();
        assertThat(containerService.stopContainer("  ", null)).isFalse();
        verifyNoInteractions(processExecutor);
    }

    @Test
    @DisplayName("restartContainer - Restarts container with configured timeout")
    void restartContainer_Success() {
        when(containerProperties.getStopTimeoutSeconds()).thenReturn(10);
        when(processExecutor.execute(commandCaptor.capture(), any(), any(), anyLong(), any()))
                .thenReturn(new ProcessResult(0, false));

        boolean restarted = containerService.restartContainer("c12345", null);

        assertThat(restarted).isTrue();
        assertThat(commandCaptor.getValue()).containsExactly("docker", "restart", "-t", "10", "c12345");
    }

    @Test
    @DisplayName("removeContainer - Removes container with force flag when specified")
    void removeContainer_Success() {
        when(processExecutor.execute(commandCaptor.capture(), any(), any(), anyLong(), any()))
                .thenReturn(new ProcessResult(0, false));

        boolean removed = containerService.removeContainer("c12345", true);

        assertThat(removed).isTrue();
        assertThat(commandCaptor.getValue()).containsExactly("docker", "rm", "-f", "c12345");
    }

    @Test
    @DisplayName("isContainerRunning - Correctly detects running state")
    void isContainerRunning_Detection() {
        doAnswer(inv -> {
            BuildLogBuffer buf = inv.getArgument(2);
            if (buf != null) {
                buf.append(LogSource.STDOUT, "true");
            }
            return new ProcessResult(0, false);
        }).when(processExecutor).execute(any(), any(), any(), anyLong(), any());

        assertThat(containerService.isContainerRunning("c12345")).isTrue();

        doAnswer(inv -> {
            BuildLogBuffer buf = inv.getArgument(2);
            if (buf != null) {
                buf.append(LogSource.STDOUT, "false");
            }
            return new ProcessResult(0, false);
        }).when(processExecutor).execute(any(), any(), any(), anyLong(), any());

        assertThat(containerService.isContainerRunning("c12345")).isFalse();
    }

    @Test
    @DisplayName("inspectContainer - Parses formatted inspection tokens into ContainerInfo")
    void inspectContainer_Success() {
        String inspectOutput = "cid999|/shipit-app-demo|running|0|2026-11-20T12:00:00Z|0001-01-01T00:00:00Z|172.17.0.2";

        doAnswer(inv -> {
            BuildLogBuffer buf = inv.getArgument(2);
            if (buf != null) {
                buf.append(LogSource.STDOUT, inspectOutput);
            }
            return new ProcessResult(0, false);
        }).when(processExecutor).execute(any(), any(), any(), anyLong(), any());

        ContainerInfo info = containerService.inspectContainer("cid999");

        assertThat(info).isNotNull();
        assertThat(info.getContainerId()).isEqualTo("cid999");
        assertThat(info.getContainerName()).isEqualTo("shipit-app-demo");
        assertThat(info.getStatus()).isEqualTo(ContainerStatus.RUNNING);
        assertThat(info.getExitCode()).isEqualTo(0);
        assertThat(info.getStartedAt()).isNotNull();
        assertThat(info.getFinishedAt()).isNull(); // starts with 0001-01-01
        assertThat(info.getIpAddress()).isEqualTo("172.17.0.2");
    }

    @Test
    @DisplayName("getContainerLogs - Retrieves tail log output")
    void getContainerLogs_Success() {
        doAnswer(inv -> {
            BuildLogBuffer buf = inv.getArgument(2);
            if (buf != null) {
                buf.append(LogSource.STDOUT, "Application initialized on port 8080");
            }
            return new ProcessResult(0, false);
        }).when(processExecutor).execute(any(), any(), any(), anyLong(), any());

        String logs = containerService.getContainerLogs("cid999", 50);

        assertThat(logs).contains("Application initialized on port 8080");
    }
}

package com.shipit.backend.container.health;

import com.shipit.backend.build.logging.BuildLogBuffer;
import com.shipit.backend.container.config.ContainerProperties;
import com.shipit.backend.container.exception.ContainerException;
import com.shipit.backend.container.model.ContainerInfo;
import com.shipit.backend.container.model.ContainerStatus;
import com.shipit.backend.container.service.DockerContainerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContainerHealthCheckerTest {

    @Mock
    private DockerContainerService dockerContainerService;

    private ContainerProperties containerProperties;
    private BuildLogBuffer logBuffer;

    @BeforeEach
    void setUp() {
        containerProperties = new ContainerProperties();
        containerProperties.setHealthCheckTimeoutSeconds(3);
        containerProperties.setHealthCheckIntervalSeconds(1);
        containerProperties.setHealthCheckPath("/");

        logBuffer = new BuildLogBuffer(100, 1024);
    }

    @Test
    @DisplayName("verifyHealth - Immediate HTTP 200 returns true and records success in log buffer")
    void verifyHealth_ImmediateSuccess() {
        ContainerHealthChecker healthChecker = new ContainerHealthChecker(dockerContainerService, containerProperties) {
            @Override
            protected int executeProbe(String targetUrl) {
                return 200;
            }
        };

        when(dockerContainerService.inspectContainer("c123")).thenReturn(
                ContainerInfo.builder().containerId("c123").status(ContainerStatus.RUNNING).build()
        );

        boolean healthy = healthChecker.verifyHealth("c123", 10050, null, logBuffer);

        assertThat(healthy).isTrue();
        assertThat(logBuffer.getAllLogsAsString()).contains("Health check passed");
    }

    @Test
    @DisplayName("verifyHealth - Retries on connection failure and succeeds when server responds")
    void verifyHealth_RetryThenSuccess() {
        AtomicInteger attempts = new AtomicInteger(0);

        ContainerHealthChecker healthChecker = new ContainerHealthChecker(dockerContainerService, containerProperties) {
            @Override
            protected int executeProbe(String targetUrl) throws Exception {
                if (attempts.incrementAndGet() == 1) {
                    throw new IOException("Connection refused");
                }
                return 200;
            }
        };

        when(dockerContainerService.inspectContainer("c123")).thenReturn(
                ContainerInfo.builder().containerId("c123").status(ContainerStatus.RUNNING).build()
        );

        boolean healthy = healthChecker.verifyHealth("c123", 10050, "/health", logBuffer);

        assertThat(healthy).isTrue();
        assertThat(attempts.get()).isEqualTo(2);
        assertThat(logBuffer.getAllLogsAsString()).contains("attempt 2: HTTP 200 received");
    }

    @Test
    @DisplayName("verifyHealth - Considers HTTP 404/401/302 as active web server response")
    void verifyHealth_AcceptsOtherHealthyCodes() {
        ContainerHealthChecker healthChecker = new ContainerHealthChecker(dockerContainerService, containerProperties) {
            @Override
            protected int executeProbe(String targetUrl) {
                return 404; // Unmapped root route in Spring Boot
            }
        };

        when(dockerContainerService.inspectContainer("c123")).thenReturn(
                ContainerInfo.builder().containerId("c123").status(ContainerStatus.RUNNING).build()
        );

        boolean healthy = healthChecker.verifyHealth("c123", 10050, "/", logBuffer);

        assertThat(healthy).isTrue();
        assertThat(logBuffer.getAllLogsAsString()).contains("HTTP 404 received");
    }

    @Test
    @DisplayName("verifyHealth - Fast-fails immediately when container crashes without waiting for timeout")
    void verifyHealth_FastFailOnCrash() {
        ContainerHealthChecker healthChecker = new ContainerHealthChecker(dockerContainerService, containerProperties) {
            @Override
            protected int executeProbe(String targetUrl) throws Exception {
                throw new IOException("Connection refused");
            }
        };

        when(dockerContainerService.inspectContainer("c123")).thenReturn(
                ContainerInfo.builder()
                        .containerId("c123")
                        .status(ContainerStatus.EXITED)
                        .exitCode(137)
                        .build()
        );
        when(dockerContainerService.getContainerLogs(eq("c123"), anyInt()))
                .thenReturn("Fatal error: OutOfMemoryError");

        long start = System.currentTimeMillis();

        assertThatThrownBy(() -> healthChecker.verifyHealth("c123", 10050, null, logBuffer))
                .isInstanceOf(ContainerException.class)
                .hasMessageContaining("crashed with exit code 137")
                .hasMessageContaining("OutOfMemoryError");

        long duration = System.currentTimeMillis() - start;
        // Fast-fail should take less than 1 second, well before the 3 second timeout
        assertThat(duration).isLessThan(2000);
        assertThat(logBuffer.getAllLogsAsString()).contains("crashed with exit code 137");
    }

    @Test
    @DisplayName("verifyHealth - Throws CONTAINER_HEALTH_CHECK_TIMEOUT when timeout expires")
    void verifyHealth_Timeout() {
        ContainerHealthChecker healthChecker = new ContainerHealthChecker(dockerContainerService, containerProperties) {
            @Override
            protected int executeProbe(String targetUrl) throws Exception {
                throw new IOException("Connection refused");
            }
        };

        when(dockerContainerService.inspectContainer("c123")).thenReturn(
                ContainerInfo.builder().containerId("c123").status(ContainerStatus.RUNNING).build()
        );
        when(dockerContainerService.getContainerLogs(eq("c123"), anyInt()))
                .thenReturn("Waiting for database connection...");

        assertThatThrownBy(() -> healthChecker.verifyHealth("c123", 10050, null, logBuffer))
                .isInstanceOf(ContainerException.class)
                .hasMessageContaining("timed out after 3s")
                .hasMessageContaining("Waiting for database connection...");

        assertThat(logBuffer.getAllLogsAsString()).contains("Health check timed out");
    }

    @Test
    @DisplayName("verifyHealth - Throws IllegalArgumentException on invalid arguments")
    void verifyHealth_InvalidArgs() {
        ContainerHealthChecker healthChecker = new ContainerHealthChecker(dockerContainerService, containerProperties);

        assertThatThrownBy(() -> healthChecker.verifyHealth(null, 10050, "/", logBuffer))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> healthChecker.verifyHealth("  ", 10050, "/", logBuffer))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> healthChecker.verifyHealth("c123", 0, "/", logBuffer))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> healthChecker.verifyHealth("c123", 70000, "/", logBuffer))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

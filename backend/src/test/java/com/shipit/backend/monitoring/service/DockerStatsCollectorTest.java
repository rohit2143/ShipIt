package com.shipit.backend.monitoring.service;

import com.shipit.backend.build.logging.BuildLogBuffer;
import com.shipit.backend.build.logging.LogSource;
import com.shipit.backend.build.process.ProcessExecutor;
import com.shipit.backend.build.process.ProcessResult;
import com.shipit.backend.monitoring.config.MonitoringProperties;
import com.shipit.backend.monitoring.model.ContainerMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DockerStatsCollectorTest {

    @Mock
    private ProcessExecutor processExecutor;

    private MonitoringProperties properties;
    private DockerStatsCollector statsCollector;

    @BeforeEach
    void setUp() {
        properties = new MonitoringProperties();
        properties.setStatsTimeoutSeconds(5);
        statsCollector = new DockerStatsCollector(processExecutor, properties);
    }

    @Test
    @DisplayName("collectStats - Successfully parses metrics for running container")
    void collectStats_RunningContainer() {
        String containerId = "c-1234567890ab";

        // Inspect command mock
        when(processExecutor.execute(
                eq(List.of("docker", "inspect", "--format", "{{.State.Status}}|{{.RestartCount}}|{{.State.StartedAt}}|{{.State.Running}}|{{.State.ExitCode}}", containerId)),
                any(), any(BuildLogBuffer.class), anyLong(), any()
        )).thenAnswer(inv -> {
            BuildLogBuffer buf = inv.getArgument(2);
            buf.append(LogSource.STDOUT, "running|2|2026-11-20T10:00:00Z|true|0");
            return ProcessResult.builder().exitCode(0).build();
        });

        // Stats command mock
        when(processExecutor.execute(
                eq(List.of("docker", "stats", "--no-stream", "--format", "{{.CPUPerc}}|{{.MemUsage}}|{{.MemPerc}}|{{.PIDs}}", containerId)),
                any(), any(BuildLogBuffer.class), anyLong(), any()
        )).thenAnswer(inv -> {
            BuildLogBuffer buf = inv.getArgument(2);
            buf.append(LogSource.STDOUT, "0.45%|45.2MiB / 512MiB|8.82%|14");
            return ProcessResult.builder().exitCode(0).build();
        });

        ContainerMetrics metrics = statsCollector.collectStats(containerId);

        assertThat(metrics.getContainerId()).isEqualTo(containerId);
        assertThat(metrics.isRunning()).isTrue();
        assertThat(metrics.getStatus()).isEqualTo("running");
        assertThat(metrics.getCpuPercent()).isEqualTo(0.45);
        assertThat(metrics.getMemoryUsageFormatted()).isEqualTo("45.2MiB / 512MiB");
        assertThat(metrics.getMemoryUsageBytes()).isEqualTo((long) (45.2 * 1024 * 1024));
        assertThat(metrics.getMemoryLimitBytes()).isEqualTo(512L * 1024 * 1024);
        assertThat(metrics.getMemoryPercent()).isEqualTo(8.82);
        assertThat(metrics.getPidsCount()).isEqualTo(14);
        assertThat(metrics.getRestartCount()).isEqualTo(2);
        assertThat(metrics.getUptimeSeconds()).isGreaterThan(0L);
    }

    @Test
    @DisplayName("collectStats - Returns stopped metrics when container is exited")
    void collectStats_StoppedContainer() {
        String containerId = "c-stopped-xyz";

        when(processExecutor.execute(
                eq(List.of("docker", "inspect", "--format", "{{.State.Status}}|{{.RestartCount}}|{{.State.StartedAt}}|{{.State.Running}}|{{.State.ExitCode}}", containerId)),
                any(), any(BuildLogBuffer.class), anyLong(), any()
        )).thenAnswer(inv -> {
            BuildLogBuffer buf = inv.getArgument(2);
            buf.append(LogSource.STDOUT, "exited|1|2026-11-20T10:00:00Z|false|137");
            return ProcessResult.builder().exitCode(0).build();
        });

        ContainerMetrics metrics = statsCollector.collectStats(containerId);

        assertThat(metrics.getContainerId()).isEqualTo(containerId);
        assertThat(metrics.isRunning()).isFalse();
        assertThat(metrics.getStatus()).isEqualTo("exited");
        assertThat(metrics.getRestartCount()).isEqualTo(1);
        assertThat(metrics.getCpuPercent()).isEqualTo(0.0);
        assertThat(metrics.getUptimeSeconds()).isEqualTo(0L);
    }

    @Test
    @DisplayName("collectStats - Handles inspect failure by returning stopped metrics")
    void collectStats_InspectFailure() {
        String containerId = "c-non-existent";

        when(processExecutor.execute(
                eq(List.of("docker", "inspect", "--format", "{{.State.Status}}|{{.RestartCount}}|{{.State.StartedAt}}|{{.State.Running}}|{{.State.ExitCode}}", containerId)),
                any(), any(BuildLogBuffer.class), anyLong(), any()
        )).thenReturn(ProcessResult.builder().exitCode(1).build());

        ContainerMetrics metrics = statsCollector.collectStats(containerId);

        assertThat(metrics.getContainerId()).isEqualTo(containerId);
        assertThat(metrics.isRunning()).isFalse();
        assertThat(metrics.getStatus()).isEqualTo("stopped");
    }

    @Test
    @DisplayName("collectStats - Returns stopped metrics on null or blank ID without executing command")
    void collectStats_NullOrBlank() {
        ContainerMetrics m1 = statsCollector.collectStats(null);
        ContainerMetrics m2 = statsCollector.collectStats("   ");

        assertThat(m1.isRunning()).isFalse();
        assertThat(m2.isRunning()).isFalse();
        verifyNoInteractions(processExecutor);
    }

    @Test
    @DisplayName("parseBytes - Correctly calculates bytes from Docker unit representations")
    void parseBytes_VariousUnits() {
        assertThat(statsCollector.parseBytes("500B")).isEqualTo(500L);
        assertThat(statsCollector.parseBytes("10KiB")).isEqualTo(10L * 1024);
        assertThat(statsCollector.parseBytes("10kB")).isEqualTo(10L * 1000);
        assertThat(statsCollector.parseBytes("50MiB")).isEqualTo(50L * 1024 * 1024);
        assertThat(statsCollector.parseBytes("2GiB")).isEqualTo(2L * 1024 * 1024 * 1024);
        assertThat(statsCollector.parseBytes("1.5GB")).isEqualTo(1500000000L);
        assertThat(statsCollector.parseBytes(null)).isEqualTo(0L);
        assertThat(statsCollector.parseBytes("")).isEqualTo(0L);
        assertThat(statsCollector.parseBytes("not-a-size")).isEqualTo(0L);
    }
}

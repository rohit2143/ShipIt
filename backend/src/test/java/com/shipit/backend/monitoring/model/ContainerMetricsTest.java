package com.shipit.backend.monitoring.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ContainerMetricsTest {

    @Test
    @DisplayName("ContainerMetrics - Factory stopped method initializes inactive metrics")
    void stopped_InitializesCorrectly() {
        ContainerMetrics metrics = ContainerMetrics.stopped("c-12345");

        assertThat(metrics.getContainerId()).isEqualTo("c-12345");
        assertThat(metrics.getStatus()).isEqualTo("stopped");
        assertThat(metrics.isRunning()).isFalse();
        assertThat(metrics.getCpuPercent()).isEqualTo(0.0);
        assertThat(metrics.getMemoryUsageBytes()).isEqualTo(0L);
        assertThat(metrics.getMemoryLimitBytes()).isEqualTo(0L);
        assertThat(metrics.getMemoryPercent()).isEqualTo(0.0);
        assertThat(metrics.getPidsCount()).isEqualTo(0);
        assertThat(metrics.getRestartCount()).isEqualTo(0);
        assertThat(metrics.getUptimeSeconds()).isEqualTo(0L);
        assertThat(metrics.getCollectedAt()).isNotNull();
    }

    @Test
    @DisplayName("ContainerMetrics - Builder initializes active metrics")
    void builder_InitializesActiveMetrics() {
        Instant started = Instant.parse("2026-11-20T10:00:00Z");

        ContainerMetrics metrics = ContainerMetrics.builder()
                .containerId("c-999")
                .status("running")
                .running(true)
                .cpuPercent(1.45)
                .memoryUsageFormatted("45.2MiB / 512MiB")
                .memoryUsageBytes(47395635L)
                .memoryLimitBytes(536870912L)
                .memoryPercent(8.83)
                .pidsCount(12)
                .restartCount(1)
                .startedAt(started)
                .uptimeSeconds(3600L)
                .build();

        assertThat(metrics.getContainerId()).isEqualTo("c-999");
        assertThat(metrics.isRunning()).isTrue();
        assertThat(metrics.getCpuPercent()).isEqualTo(1.45);
        assertThat(metrics.getMemoryUsageFormatted()).isEqualTo("45.2MiB / 512MiB");
        assertThat(metrics.getMemoryUsageBytes()).isEqualTo(47395635L);
        assertThat(metrics.getMemoryLimitBytes()).isEqualTo(536870912L);
        assertThat(metrics.getMemoryPercent()).isEqualTo(8.83);
        assertThat(metrics.getPidsCount()).isEqualTo(12);
        assertThat(metrics.getRestartCount()).isEqualTo(1);
        assertThat(metrics.getUptimeSeconds()).isEqualTo(3600L);
    }
}

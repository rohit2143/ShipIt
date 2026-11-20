package com.shipit.backend.monitoring.service;

import com.shipit.backend.build.logging.BuildLogBuffer;
import com.shipit.backend.build.logging.BuildLogEntry;
import com.shipit.backend.build.logging.LogSource;
import com.shipit.backend.build.process.ProcessExecutor;
import com.shipit.backend.build.process.ProcessResult;
import com.shipit.backend.monitoring.config.MonitoringProperties;
import com.shipit.backend.monitoring.model.ContainerMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service that collects real-time hardware resource and process lifecycle metrics
 * from the Docker daemon using the CLI.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DockerStatsCollector {

    private static final Pattern SIZE_PATTERN = Pattern.compile("^([0-9.]+)\\s*([a-zA-Z]+)$");

    private final ProcessExecutor processExecutor;
    private final MonitoringProperties monitoringProperties;

    /**
     * Gathers a snapshot of container health, CPU, memory, PIDs, and uptime.
     *
     * @param containerId Target Docker container ID.
     * @return ContainerMetrics snapshot, or stopped metrics if unavailable.
     */
    public ContainerMetrics collectStats(String containerId) {
        if (containerId == null || containerId.isBlank()) {
            return ContainerMetrics.stopped(containerId);
        }

        long timeout = monitoringProperties != null ? monitoringProperties.getStatsTimeoutSeconds() : 5L;

        // 1. Inspect container lifecycle status, restart count, and start timestamp
        List<String> inspectCmd = List.of(
                "docker", "inspect",
                "--format", "{{.State.Status}}|{{.RestartCount}}|{{.State.StartedAt}}|{{.State.Running}}|{{.State.ExitCode}}",
                containerId
        );

        BuildLogBuffer inspectBuffer = new BuildLogBuffer(20, 1024);
        ProcessResult inspectResult = processExecutor.execute(inspectCmd, null, inspectBuffer, timeout, Set.of());

        if (inspectResult.getExitCode() != 0) {
            log.debug("Container {} inspect returned code {}", containerId, inspectResult.getExitCode());
            return ContainerMetrics.stopped(containerId);
        }

        String inspectLine = extractStdout(inspectBuffer);
        if (inspectLine == null || !inspectLine.contains("|")) {
            return ContainerMetrics.stopped(containerId);
        }

        String[] inspectParts = inspectLine.split("\\|", -1);
        String status = inspectParts.length > 0 ? inspectParts[0].trim().toLowerCase() : "unknown";
        int restartCount = (inspectParts.length > 1) ? parseIntSafe(inspectParts[1]) : 0;
        Instant startedAt = (inspectParts.length > 2) ? parseInstantSafe(inspectParts[2]) : null;
        boolean running = (inspectParts.length > 3) && Boolean.parseBoolean(inspectParts[3].trim());

        long uptimeSeconds = (running && startedAt != null)
                ? Math.max(0L, Duration.between(startedAt, Instant.now()).toSeconds())
                : 0L;

        if (!running) {
            return ContainerMetrics.builder()
                    .containerId(containerId)
                    .status(status)
                    .running(false)
                    .restartCount(restartCount)
                    .startedAt(startedAt)
                    .uptimeSeconds(0L)
                    .collectedAt(Instant.now())
                    .build();
        }

        // 2. Query docker stats for CPU, memory usage/percentage, and thread count
        List<String> statsCmd = List.of(
                "docker", "stats", "--no-stream",
                "--format", "{{.CPUPerc}}|{{.MemUsage}}|{{.MemPerc}}|{{.PIDs}}",
                containerId
        );

        BuildLogBuffer statsBuffer = new BuildLogBuffer(20, 1024);
        ProcessResult statsResult = processExecutor.execute(statsCmd, null, statsBuffer, timeout, Set.of());

        if (statsResult.getExitCode() != 0) {
            return ContainerMetrics.builder()
                    .containerId(containerId)
                    .status(status)
                    .running(true)
                    .restartCount(restartCount)
                    .startedAt(startedAt)
                    .uptimeSeconds(uptimeSeconds)
                    .collectedAt(Instant.now())
                    .build();
        }

        String statsLine = extractStdout(statsBuffer);
        if (statsLine == null || !statsLine.contains("|")) {
            return ContainerMetrics.builder()
                    .containerId(containerId)
                    .status(status)
                    .running(true)
                    .restartCount(restartCount)
                    .startedAt(startedAt)
                    .uptimeSeconds(uptimeSeconds)
                    .collectedAt(Instant.now())
                    .build();
        }

        String[] statsParts = statsLine.split("\\|", -1);
        double cpuPercent = (statsParts.length > 0) ? parsePercentSafe(statsParts[0]) : 0.0;
        String memUsageRaw = (statsParts.length > 1) ? statsParts[1].trim() : "0B / 0B";
        double memPercent = (statsParts.length > 2) ? parsePercentSafe(statsParts[2]) : 0.0;
        int pids = (statsParts.length > 3) ? parseIntSafe(statsParts[3]) : 0;

        long usageBytes = 0L;
        long limitBytes = 0L;
        if (memUsageRaw.contains("/")) {
            String[] memSplit = memUsageRaw.split("/", 2);
            usageBytes = parseBytes(memSplit[0].trim());
            limitBytes = parseBytes(memSplit[1].trim());
        }

        return ContainerMetrics.builder()
                .containerId(containerId)
                .status(status)
                .running(true)
                .cpuPercent(cpuPercent)
                .memoryUsageFormatted(memUsageRaw)
                .memoryUsageBytes(usageBytes)
                .memoryLimitBytes(limitBytes)
                .memoryPercent(memPercent)
                .pidsCount(pids)
                .restartCount(restartCount)
                .startedAt(startedAt)
                .uptimeSeconds(uptimeSeconds)
                .collectedAt(Instant.now())
                .build();
    }

    /**
     * Parses byte unit strings produced by Docker (e.g. 45.2MiB, 512MB, 1.5GiB).
     */
    public long parseBytes(String sizeStr) {
        if (sizeStr == null || sizeStr.isBlank()) {
            return 0L;
        }

        Matcher matcher = SIZE_PATTERN.matcher(sizeStr.trim());
        if (!matcher.matches()) {
            return 0L;
        }

        try {
            double value = Double.parseDouble(matcher.group(1));
            String unit = matcher.group(2).toUpperCase(Locale.ROOT);

            return switch (unit) {
                case "B" -> (long) value;
                case "KIB" -> (long) (value * 1024);
                case "KB" -> (long) (value * 1000);
                case "MIB" -> (long) (value * 1024 * 1024);
                case "MB" -> (long) (value * 1000 * 1000);
                case "GIB" -> (long) (value * 1024 * 1024 * 1024);
                case "GB" -> (long) (value * 1000 * 1000 * 1000);
                case "TIB" -> (long) (value * 1024L * 1024L * 1024L * 1024L);
                case "TB" -> (long) (value * 1000L * 1000L * 1000L * 1000L);
                default -> (long) value;
            };
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private String extractStdout(BuildLogBuffer buffer) {
        for (BuildLogEntry entry : buffer.getEntries()) {
            if (entry.getSource() == LogSource.STDOUT && entry.getMessage() != null && !entry.getMessage().isBlank()) {
                return entry.getMessage().trim();
            }
        }
        return null;
    }

    private double parsePercentSafe(String str) {
        if (str == null) return 0.0;
        try {
            return Double.parseDouble(str.replace("%", "").trim());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private int parseIntSafe(String str) {
        if (str == null) return 0;
        try {
            return Integer.parseInt(str.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private Instant parseInstantSafe(String str) {
        if (str == null || str.isBlank() || "0001-01-01T00:00:00Z".equals(str.trim())) {
            return null;
        }
        try {
            return Instant.parse(str.trim());
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}

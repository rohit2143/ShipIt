package com.shipit.backend.container.service;

import com.shipit.backend.build.logging.BuildLogBuffer;
import com.shipit.backend.build.logging.BuildLogEntry;
import com.shipit.backend.build.logging.LogSource;
import com.shipit.backend.build.process.ProcessExecutor;
import com.shipit.backend.build.process.ProcessResult;
import com.shipit.backend.container.config.ContainerProperties;
import com.shipit.backend.container.exception.ContainerException;
import com.shipit.backend.container.model.ContainerConfig;
import com.shipit.backend.container.model.ContainerInfo;
import com.shipit.backend.container.model.ContainerStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * Service responsible for managing the execution, inspection, and lifecycle
 * of Docker application containers.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DockerContainerService {

    private final ProcessExecutor processExecutor;
    private final ContainerProperties containerProperties;

    /**
     * Spawns a new Docker container for the given deployment configuration.
     *
     * @param config Container execution specification.
     * @param logBuffer Buffer for capturing stdout/stderr startup output.
     * @return Unique 64-character or truncated container ID.
     */
    public String runContainer(ContainerConfig config, BuildLogBuffer logBuffer) {
        if (config == null || !config.isValid()) {
            throw new IllegalArgumentException("ContainerConfig must not be null and must be valid");
        }

        List<String> command = new ArrayList<>();
        command.add("docker");
        command.add("run");
        command.add("-d");
        command.add("--name");
        command.add(config.getContainerName());
        command.add("-p");
        command.add(config.getHostPort() + ":" + config.getContainerPort());

        if (config.getMemoryLimit() != null && !config.getMemoryLimit().isBlank()) {
            command.add("-m");
            command.add(config.getMemoryLimit());
        }

        if (config.getCpuLimit() != null && !config.getCpuLimit().isBlank()) {
            command.add("--cpus");
            command.add(config.getCpuLimit());
        }

        if (config.getRestartPolicy() != null && !config.getRestartPolicy().isBlank()) {
            command.add("--restart");
            command.add(config.getRestartPolicy());
        }

        Set<String> sensitiveValues = new HashSet<>();
        if (config.getEnvironmentVariables() != null) {
            for (Map.Entry<String, String> entry : config.getEnvironmentVariables().entrySet()) {
                command.add("-e");
                command.add(entry.getKey() + "=" + (entry.getValue() != null ? entry.getValue() : ""));
                if (entry.getValue() != null && !entry.getValue().isBlank()) {
                    sensitiveValues.add(entry.getValue());
                }
            }
        }

        command.add(config.getImageTag());

        if (logBuffer != null) {
            logBuffer.append(LogSource.SYSTEM, String.format("Creating container '%s' on port %d:%d...",
                    config.getContainerName(), config.getHostPort(), config.getContainerPort()));
        }

        ProcessResult result = processExecutor.execute(
                command,
                null,
                logBuffer,
                60L,
                sensitiveValues
        );

        if (result.isTimedOut()) {
            throw new ContainerException(
                    "Timed out creating container for deployment: " + config.getDeploymentId(),
                    "CONTAINER_START_TIMEOUT"
            );
        }

        if (result.getExitCode() != 0) {
            throw new ContainerException(
                    "Failed to run container " + config.getContainerName() + " (exit code " + result.getExitCode() + ")",
                    "CONTAINER_START_FAILED"
            );
        }

        // Extract container ID from stdout output
        String containerId = null;
        if (logBuffer != null) {
            for (BuildLogEntry entry : logBuffer.getEntries()) {
                if (entry.getSource() == LogSource.STDOUT && entry.getMessage() != null) {
                    String candidate = entry.getMessage().trim();
                    if (candidate.matches("^[a-fA-F0-9]{12,64}$")) {
                        containerId = candidate;
                    }
                }
            }
        }

        if (containerId == null || containerId.isBlank()) {
            containerId = getContainerIdByName(config.getContainerName());
        }

        log.info("Successfully started container {} (ID: {}) on port {}",
                config.getContainerName(), containerId, config.getHostPort());
        return containerId != null ? containerId : config.getContainerName();
    }

    /**
     * Stops a running container.
     */
    public boolean stopContainer(String containerId, Integer timeoutSeconds) {
        if (containerId == null || containerId.isBlank()) {
            return false;
        }

        int timeout = timeoutSeconds != null ? timeoutSeconds : containerProperties.getStopTimeoutSeconds();
        List<String> command = List.of("docker", "stop", "-t", String.valueOf(timeout), containerId);

        BuildLogBuffer buffer = new BuildLogBuffer(100, 1024);
        ProcessResult result = processExecutor.execute(command, null, buffer, timeout + 15L, Set.of());
        boolean success = result.getExitCode() == 0;

        if (success) {
            log.info("Stopped container: {}", containerId);
        } else {
            log.warn("Failed to stop container {}: exit code {}", containerId, result.getExitCode());
        }
        return success;
    }

    /**
     * Restarts a container.
     */
    public boolean restartContainer(String containerId, Integer timeoutSeconds) {
        if (containerId == null || containerId.isBlank()) {
            return false;
        }

        int timeout = timeoutSeconds != null ? timeoutSeconds : containerProperties.getStopTimeoutSeconds();
        List<String> command = List.of("docker", "restart", "-t", String.valueOf(timeout), containerId);

        BuildLogBuffer buffer = new BuildLogBuffer(100, 1024);
        ProcessResult result = processExecutor.execute(command, null, buffer, timeout + 15L, Set.of());
        boolean success = result.getExitCode() == 0;

        if (success) {
            log.info("Restarted container: {}", containerId);
        } else {
            log.warn("Failed to restart container {}: exit code {}", containerId, result.getExitCode());
        }
        return success;
    }

    /**
     * Removes a container from the host.
     */
    public boolean removeContainer(String containerId, boolean force) {
        if (containerId == null || containerId.isBlank()) {
            return false;
        }

        List<String> command = force
                ? List.of("docker", "rm", "-f", containerId)
                : List.of("docker", "rm", containerId);

        BuildLogBuffer buffer = new BuildLogBuffer(100, 1024);
        ProcessResult result = processExecutor.execute(command, null, buffer, 30L, Set.of());
        boolean success = result.getExitCode() == 0;

        if (success) {
            log.info("Removed container: {}", containerId);
        } else {
            log.warn("Failed to remove container {}: exit code {}", containerId, result.getExitCode());
        }
        return success;
    }

    /**
     * Checks if a container is actively running.
     */
    public boolean isContainerRunning(String containerId) {
        if (containerId == null || containerId.isBlank()) {
            return false;
        }

        List<String> command = List.of("docker", "inspect", "--format", "{{.State.Running}}", containerId);
        BuildLogBuffer buffer = new BuildLogBuffer(50, 1024);
        ProcessResult result = processExecutor.execute(command, null, buffer, 10L, Set.of());

        if (result.getExitCode() != 0) {
            return false;
        }

        for (BuildLogEntry entry : buffer.getEntries()) {
            if ("true".equalsIgnoreCase(entry.getMessage().trim())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Inspects container state, IP address, and exit codes.
     */
    public ContainerInfo inspectContainer(String containerId) {
        if (containerId == null || containerId.isBlank()) {
            return null;
        }

        List<String> command = List.of(
                "docker", "inspect",
                "--format", "{{.Id}}|{{.Name}}|{{.State.Status}}|{{.State.ExitCode}}|{{.State.StartedAt}}|{{.State.FinishedAt}}|{{.NetworkSettings.IPAddress}}",
                containerId
        );

        BuildLogBuffer buffer = new BuildLogBuffer(50, 1024);
        ProcessResult result = processExecutor.execute(command, null, buffer, 15L, Set.of());

        if (result.getExitCode() != 0) {
            log.debug("Container {} inspect returned non-zero exit code: {}", containerId, result.getExitCode());
            return null;
        }

        for (BuildLogEntry entry : buffer.getEntries()) {
            if (entry.getSource() == LogSource.STDOUT && entry.getMessage() != null && entry.getMessage().contains("|")) {
                String[] parts = entry.getMessage().trim().split("\\|", -1);
                if (parts.length >= 7) {
                    String id = parts[0];
                    String name = parts[1].startsWith("/") ? parts[1].substring(1) : parts[1];
                    ContainerStatus status = parseStatus(parts[2]);
                    Integer exitCode = parseInteger(parts[3]);
                    Instant startedAt = parseInstant(parts[4]);
                    Instant finishedAt = parseInstant(parts[5]);
                    String ip = parts[6];

                    return ContainerInfo.builder()
                            .containerId(id)
                            .containerName(name)
                            .status(status)
                            .running(status == ContainerStatus.RUNNING)
                            .exitCode(exitCode)
                            .startedAt(startedAt)
                            .finishedAt(finishedAt)
                            .ipAddress(ip)
                            .build();
                }
            }
        }
        return null;
    }

    /**
     * Fetches tail lines of standard output/error logs from the container.
     */
    public String getContainerLogs(String containerId, int tailLines) {
        if (containerId == null || containerId.isBlank()) {
            return "";
        }

        int lines = tailLines > 0 ? tailLines : 100;
        List<String> command = List.of("docker", "logs", "--tail", String.valueOf(lines), containerId);

        BuildLogBuffer buffer = new BuildLogBuffer(lines + 10, 2048);
        ProcessResult result = processExecutor.execute(command, null, buffer, 20L, Set.of());

        if (result.getExitCode() != 0) {
            log.warn("Failed to retrieve logs for container {}: exit code {}", containerId, result.getExitCode());
        }
        return buffer.getAllLogsAsString();
    }

    /**
     * Looks up container ID by container name.
     */
    public String getContainerIdByName(String containerName) {
        if (containerName == null || containerName.isBlank()) {
            return null;
        }

        List<String> command = List.of("docker", "inspect", "--format", "{{.Id}}", containerName);
        BuildLogBuffer buffer = new BuildLogBuffer(20, 1024);
        ProcessResult result = processExecutor.execute(command, null, buffer, 10L, Set.of());

        if (result.getExitCode() == 0) {
            for (BuildLogEntry entry : buffer.getEntries()) {
                if (entry.getSource() == LogSource.STDOUT && entry.getMessage() != null) {
                    String candidate = entry.getMessage().trim();
                    if (candidate.matches("^[a-fA-F0-9]{12,64}$")) {
                        return candidate;
                    }
                }
            }
        }
        return null;
    }

    private ContainerStatus parseStatus(String raw) {
        if (raw == null) {
            return ContainerStatus.UNKNOWN;
        }
        return switch (raw.toLowerCase().trim()) {
            case "running" -> ContainerStatus.RUNNING;
            case "created" -> ContainerStatus.STARTING;
            case "exited" -> ContainerStatus.EXITED;
            case "paused" -> ContainerStatus.STOPPED;
            case "dead" -> ContainerStatus.UNHEALTHY;
            default -> ContainerStatus.UNKNOWN;
        };
    }

    private Integer parseInteger(String raw) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private Instant parseInstant(String raw) {
        if (raw == null || raw.isBlank() || raw.startsWith("0001-01-01")) {
            return null;
        }
        try {
            return Instant.parse(raw.trim());
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}

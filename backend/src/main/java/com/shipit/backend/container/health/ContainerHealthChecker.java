package com.shipit.backend.container.health;

import com.shipit.backend.build.logging.BuildLogBuffer;
import com.shipit.backend.build.logging.LogSource;
import com.shipit.backend.container.config.ContainerProperties;
import com.shipit.backend.container.exception.ContainerException;
import com.shipit.backend.container.model.ContainerInfo;
import com.shipit.backend.container.model.ContainerStatus;
import com.shipit.backend.container.service.DockerContainerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Service responsible for actively verifying the readiness and health
 * of newly started application containers via HTTP probes.
 */
@Slf4j
@Component
public class ContainerHealthChecker {

    private final DockerContainerService dockerContainerService;
    private final ContainerProperties containerProperties;
    private final HttpClient httpClient;

    @Autowired
    public ContainerHealthChecker(DockerContainerService dockerContainerService,
                                  ContainerProperties containerProperties) {
        this(dockerContainerService, containerProperties, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build());
    }

    public ContainerHealthChecker(DockerContainerService dockerContainerService,
                                  ContainerProperties containerProperties,
                                  HttpClient httpClient) {
        this.dockerContainerService = dockerContainerService;
        this.containerProperties = containerProperties;
        this.httpClient = httpClient;
    }

    /**
     * Polls the container HTTP endpoint until it responds with a healthy HTTP status
     * or until the configured timeout expires.
     * <p>
     * Implements fast-failure detection: if the container crashes or exits prematurely,
     * the health check aborts immediately and attaches tail container output.
     *
     * @param containerId Identifier of the target Docker container.
     * @param hostPort Port on localhost mapped to the container.
     * @param healthPath Custom health check path (or null for default).
     * @param logBuffer Buffer for recording health check events.
     * @return true if health check succeeded.
     * @throws ContainerException if health check times out or container crashes.
     */
    public boolean verifyHealth(String containerId, int hostPort, String healthPath, BuildLogBuffer logBuffer) {
        if (containerId == null || containerId.isBlank()) {
            throw new IllegalArgumentException("containerId must not be null or blank");
        }
        if (hostPort <= 0 || hostPort > 65535) {
            throw new IllegalArgumentException("Invalid hostPort: " + hostPort);
        }

        String path = (healthPath != null && !healthPath.isBlank())
                ? (healthPath.startsWith("/") ? healthPath : "/" + healthPath)
                : containerProperties.getHealthCheckPath();

        String targetUrl = "http://localhost:" + hostPort + path;
        int timeoutSeconds = Math.max(1, containerProperties.getHealthCheckTimeoutSeconds());
        int intervalSeconds = Math.max(1, containerProperties.getHealthCheckIntervalSeconds());

        if (logBuffer != null) {
            logBuffer.append(LogSource.SYSTEM, String.format(
                    "Starting health checks on %s (timeout: %ds, interval: %ds)...",
                    targetUrl, timeoutSeconds, intervalSeconds));
        }
        log.info("Starting health check probes on {} for container {}", targetUrl, containerId);

        long deadlineMs = System.currentTimeMillis() + (timeoutSeconds * 1000L);
        int attempt = 0;

        while (System.currentTimeMillis() < deadlineMs) {
            attempt++;

            // 1. Fast-fail if container has crashed or exited
            ContainerInfo containerInfo = dockerContainerService.inspectContainer(containerId);
            if (containerInfo != null && containerInfo.getStatus() == ContainerStatus.EXITED) {
                String tailLogs = dockerContainerService.getContainerLogs(containerId, 50);
                String crashMsg = String.format("Container '%s' crashed with exit code %s during startup:\n%s",
                        containerId, containerInfo.getExitCode(), tailLogs.trim());
                if (logBuffer != null) {
                    logBuffer.append(LogSource.STDERR, crashMsg);
                }
                log.error(crashMsg);
                throw new ContainerException(crashMsg, "CONTAINER_CRASHED");
            }

            // 2. Perform HTTP probe
            try {
                int statusCode = executeProbe(targetUrl);
                if (isHealthyStatus(statusCode)) {
                    if (logBuffer != null) {
                        logBuffer.append(LogSource.SYSTEM, String.format(
                                "Health check passed on attempt %d: HTTP %d received from %s", attempt, statusCode, targetUrl));
                    }
                    log.info("Container {} passed health check on attempt {} with HTTP {}", containerId, attempt, statusCode);
                    return true;
                } else {
                    log.debug("Container {} health probe returned non-healthy HTTP status: {}", containerId, statusCode);
                }
            } catch (Exception e) {
                log.debug("Container {} health probe attempt {} connection failed: {}", containerId, attempt, e.getMessage());
            }

            // Sleep before next probe
            try {
                Thread.sleep(intervalSeconds * 1000L);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new ContainerException("Health check polling interrupted for container " + containerId, "HEALTH_CHECK_INTERRUPTED");
            }
        }

        // Timeout reached
        String tailLogs = dockerContainerService.getContainerLogs(containerId, 50);
        String timeoutMsg = String.format("Health check timed out after %ds for container '%s' at %s. Tail logs:\n%s",
                timeoutSeconds, containerId, targetUrl, tailLogs.trim());

        if (logBuffer != null) {
            logBuffer.append(LogSource.STDERR, timeoutMsg);
        }
        log.error(timeoutMsg);
        throw new ContainerException(timeoutMsg, "CONTAINER_HEALTH_CHECK_TIMEOUT");
    }

    protected int executeProbe(String targetUrl) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(targetUrl))
                .timeout(Duration.ofSeconds(2))
                .GET()
                .build();

        HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
        return response.statusCode();
    }

    private boolean isHealthyStatus(int statusCode) {
        // 2xx (Success), 3xx (Redirect), 401/403/404 (Server is active and handling HTTP requests)
        return (statusCode >= 200 && statusCode < 400)
                || statusCode == 401
                || statusCode == 403
                || statusCode == 404;
    }
}

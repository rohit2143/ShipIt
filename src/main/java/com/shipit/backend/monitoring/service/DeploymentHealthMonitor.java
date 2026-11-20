package com.shipit.backend.monitoring.service;

import com.shipit.backend.common.exception.ResourceNotFoundException;
import com.shipit.backend.container.config.ContainerProperties;
import com.shipit.backend.deployment.entity.Deployment;
import com.shipit.backend.deployment.entity.DeploymentStatus;
import com.shipit.backend.deployment.repository.DeploymentRepository;
import com.shipit.backend.monitoring.config.MonitoringProperties;
import com.shipit.backend.monitoring.model.AvailabilityStatus;
import com.shipit.backend.monitoring.model.ContainerMetrics;
import com.shipit.backend.monitoring.model.DeploymentHealthReport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Service that periodically monitors active application deployments,
 * performs active HTTP readiness probes, gathers hardware metrics, and tracks availability.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeploymentHealthMonitor {

    private final DeploymentRepository deploymentRepository;
    private final DockerStatsCollector dockerStatsCollector;
    private final MonitoringProperties monitoringProperties;
    private final ContainerProperties containerProperties;

    private final Map<UUID, AtomicInteger> failureCountMap = new ConcurrentHashMap<>();
    private final Map<UUID, DeploymentHealthReport> healthCache = new ConcurrentHashMap<>();

    /**
     * Executes an immediate live health probe and resource metrics gathering for a deployment.
     */
    public DeploymentHealthReport checkDeploymentHealth(UUID deploymentId) {
        if (deploymentId == null) {
            throw new IllegalArgumentException("deploymentId must not be null");
        }

        Deployment deployment = deploymentRepository.findById(deploymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Deployment not found with id: " + deploymentId));

        return evaluateHealth(deployment);
    }

    /**
     * Returns cached health if fresh (< 15s), otherwise performs a live check.
     */
    public DeploymentHealthReport getCachedOrLiveHealth(UUID deploymentId) {
        DeploymentHealthReport cached = healthCache.get(deploymentId);
        if (cached != null && cached.getLastCheckedAt() != null) {
            long ageSeconds = Duration.between(cached.getLastCheckedAt(), Instant.now()).toSeconds();
            if (ageSeconds < 15L) {
                return cached;
            }
        }
        return checkDeploymentHealth(deploymentId);
    }

    /**
     * Evaluates health for the current active deployment of a project.
     */
    public DeploymentHealthReport getProjectActiveHealth(UUID projectId) {
        if (projectId == null) {
            throw new IllegalArgumentException("projectId must not be null");
        }

        Optional<Deployment> activeOpt = deploymentRepository
                .findTopByProjectIdAndStatusOrderByCreatedAtDesc(projectId, DeploymentStatus.SUCCESS);

        if (activeOpt.isEmpty() || activeOpt.get().getContainerId() == null) {
            return DeploymentHealthReport.builder()
                    .projectId(projectId)
                    .availability(AvailabilityStatus.STOPPED)
                    .details("No active running deployment found for project")
                    .lastCheckedAt(Instant.now())
                    .build();
        }

        return getCachedOrLiveHealth(activeOpt.get().getId());
    }

    /**
     * Background scheduler that periodically inspects all active application containers.
     */
    @Scheduled(fixedDelayString = "${shipit.monitoring.health-check-interval-seconds:30}000")
    public void checkAllActiveDeployments() {
        List<Deployment> activeDeployments = deploymentRepository.findByStatusAndContainerIdIsNotNull(DeploymentStatus.SUCCESS);
        if (activeDeployments.isEmpty()) {
            return;
        }

        log.debug("Running periodic health checks for {} active deployments", activeDeployments.size());
        for (Deployment deployment : activeDeployments) {
            try {
                evaluateHealth(deployment);
            } catch (Exception e) {
                log.warn("Error running health check on deployment {}: {}", deployment.getId(), e.getMessage());
            }
        }
    }

    private DeploymentHealthReport evaluateHealth(Deployment deployment) {
        UUID deploymentId = deployment.getId();
        UUID projectId = deployment.getProject() != null ? deployment.getProject().getId() : null;
        String containerId = deployment.getContainerId();
        Integer port = deployment.getContainerPort();

        if (deployment.getStatus() != DeploymentStatus.SUCCESS || containerId == null || port == null || port <= 0) {
            DeploymentHealthReport report = DeploymentHealthReport.builder()
                    .deploymentId(deploymentId)
                    .projectId(projectId)
                    .containerId(containerId)
                    .hostPort(port)
                    .availability(AvailabilityStatus.STOPPED)
                    .containerRunning(false)
                    .details("Deployment is not in active SUCCESS state (status: " + deployment.getStatus() + ")")
                    .lastCheckedAt(Instant.now())
                    .build();
            healthCache.put(deploymentId, report);
            return report;
        }

        // 1. Collect real-time Docker container metrics
        ContainerMetrics metrics = dockerStatsCollector.collectStats(containerId);

        if (!metrics.isRunning()) {
            int failures = failureCountMap.computeIfAbsent(deploymentId, k -> new AtomicInteger(0)).incrementAndGet();
            DeploymentHealthReport report = DeploymentHealthReport.builder()
                    .deploymentId(deploymentId)
                    .projectId(projectId)
                    .containerId(containerId)
                    .hostPort(port)
                    .availability(AvailabilityStatus.UNHEALTHY)
                    .containerRunning(false)
                    .consecutiveFailures(failures)
                    .metrics(metrics)
                    .details(String.format("Container '%s' is not running (status: %s)", containerId, metrics.getStatus()))
                    .lastCheckedAt(Instant.now())
                    .build();
            healthCache.put(deploymentId, report);
            return report;
        }

        // 2. Perform HTTP readiness probe
        String path = (containerProperties != null && containerProperties.getHealthCheckPath() != null)
                ? containerProperties.getHealthCheckPath()
                : "/";

        int timeoutSec = (monitoringProperties != null) ? monitoringProperties.getHealthCheckTimeoutSeconds() : 5;
        ProbeResult probe = probeHttp(port, path, timeoutSec);

        int maxThreshold = (monitoringProperties != null) ? monitoringProperties.getMaxUnhealthyThreshold() : 3;

        AvailabilityStatus availability;
        String details;
        int failures = 0;

        if (probe.isSuccess()) {
            failureCountMap.remove(deploymentId);
            availability = AvailabilityStatus.HEALTHY;
            details = String.format("Application is healthy (HTTP %d)", probe.getStatusCode());
        } else {
            failures = failureCountMap.computeIfAbsent(deploymentId, k -> new AtomicInteger(0)).incrementAndGet();
            if (failures < maxThreshold) {
                availability = AvailabilityStatus.DEGRADED;
                details = String.format("HTTP probe failed (%d/%d): %s", failures, maxThreshold, probe.getError());
            } else {
                availability = AvailabilityStatus.UNHEALTHY;
                details = String.format("HTTP probe failed consistently (%d failures): %s", failures, probe.getError());
            }
        }

        DeploymentHealthReport report = DeploymentHealthReport.builder()
                .deploymentId(deploymentId)
                .projectId(projectId)
                .containerId(containerId)
                .hostPort(port)
                .availability(availability)
                .httpStatus(probe.getStatusCode())
                .httpProbePassed(probe.isSuccess())
                .containerRunning(true)
                .consecutiveFailures(failures)
                .metrics(metrics)
                .details(details)
                .lastCheckedAt(Instant.now())
                .build();

        healthCache.put(deploymentId, report);
        return report;
    }

    /**
     * Executes HTTP probe against local host port.
     */
    protected ProbeResult probeHttp(int port, String path, int timeoutSeconds) {
        String cleanPath = path.startsWith("/") ? path : "/" + path;
        String urlString = String.format("http://localhost:%d%s", port, cleanPath);

        HttpURLConnection conn = null;
        try {
            URL url = URI.create(urlString).toURL();
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(timeoutSeconds * 1000);
            conn.setReadTimeout(timeoutSeconds * 1000);
            conn.setInstanceFollowRedirects(true);

            int responseCode = conn.getResponseCode();
            boolean success = responseCode >= 200 && responseCode < 400;

            return new ProbeResult(success, responseCode, success ? null : "HTTP " + responseCode);
        } catch (IOException e) {
            return new ProbeResult(false, null, e.getMessage());
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    public record ProbeResult(boolean isSuccess, Integer getStatusCode, String getError) {}

    public void clearCache() {
        healthCache.clear();
        failureCountMap.clear();
    }
}

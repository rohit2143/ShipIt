package com.shipit.backend.build.service;

import com.shipit.backend.build.dto.BuildLogsResponse;
import com.shipit.backend.common.exception.ResourceNotFoundException;
import com.shipit.backend.deployment.entity.Deployment;
import com.shipit.backend.deployment.entity.DeploymentStatus;
import com.shipit.backend.deployment.repository.DeploymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Service responsible for storing and retrieving deployment build execution logs.
 * <p>
 * Implements a memory-bounded LRU cache for build logs to keep memory usage minimal
 * within an 8 GB RAM development environment.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BuildLogService {

    public static final int MAX_STORED_LOGS = 100;

    private final DeploymentRepository deploymentRepository;

    /**
     * Bounded, thread-safe LRU cache retaining the most recent deployment build logs.
     */
    private final Map<UUID, String> logStorage = Collections.synchronizedMap(
            new LinkedHashMap<UUID, String>(MAX_STORED_LOGS, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<UUID, String> eldest) {
                    return size() > MAX_STORED_LOGS;
                }
            }
    );

    /**
     * Stores the build execution logs for a deployment in memory.
     *
     * @param deploymentId ID of the completed or running deployment.
     * @param logs Consolidated log lines.
     */
    public void storeLogs(UUID deploymentId, String logs) {
        if (deploymentId == null) {
            return;
        }
        String sanitized = logs != null ? logs : "";
        logStorage.put(deploymentId, sanitized);
        log.debug("Stored build logs for deployment {} ({} chars)", deploymentId, sanitized.length());
    }

    /**
     * Retrieves build logs for a deployment, verifying that the requesting user owns the deployment's project.
     *
     * @param userId ID of the authenticated user.
     * @param deploymentId Target deployment ID.
     * @return Formatted BuildLogsResponse.
     * @throws ResourceNotFoundException if deployment does not exist or user does not own it.
     */
    public BuildLogsResponse getLogs(UUID userId, UUID deploymentId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId must not be null");
        }
        if (deploymentId == null) {
            throw new IllegalArgumentException("deploymentId must not be null");
        }

        Deployment deployment = deploymentRepository.findByIdAndUserId(deploymentId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Deployment not found with id: " + deploymentId));

        String logs = logStorage.get(deploymentId);
        if (logs == null) {
            if (deployment.getStatus() == DeploymentStatus.QUEUED) {
                logs = "Deployment is currently queued. Build logs not yet available.";
            } else {
                logs = "No build logs recorded for deployment " + deploymentId;
            }
        }

        int lineCount = logs.isBlank() ? 0 : (int) logs.lines().count();

        return BuildLogsResponse.builder()
                .deploymentId(deploymentId)
                .logs(logs)
                .lineCount(lineCount)
                .build();
    }

    /**
     * Checks if build logs currently exist in memory for the given deployment.
     */
    public boolean hasLogs(UUID deploymentId) {
        return deploymentId != null && logStorage.containsKey(deploymentId);
    }

    /**
     * Returns raw logs without ownership verification (internal use).
     */
    public Optional<String> getRawLogs(UUID deploymentId) {
        if (deploymentId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(logStorage.get(deploymentId));
    }

    /**
     * Clears all stored build logs from memory.
     */
    public void clear() {
        logStorage.clear();
    }
}

package com.shipit.backend.container.service;

import com.shipit.backend.common.exception.ConflictException;
import com.shipit.backend.common.exception.ResourceNotFoundException;
import com.shipit.backend.container.config.ContainerProperties;
import com.shipit.backend.container.dto.ContainerLogsResponse;
import com.shipit.backend.container.model.ContainerInfo;
import com.shipit.backend.container.model.ContainerStatus;
import com.shipit.backend.deployment.entity.Deployment;
import com.shipit.backend.deployment.repository.DeploymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContainerManagementService {

    private final DeploymentRepository deploymentRepository;
    private final DockerContainerService dockerContainerService;
    private final ContainerProperties containerProperties;

    @Transactional(readOnly = true)
    public ContainerInfo inspectDeploymentContainer(UUID userId, UUID deploymentId) {
        log.debug("Inspecting container for deployment id: {} user: {}", deploymentId, userId);
        Deployment deployment = getDeploymentForUser(userId, deploymentId);

        if (deployment.getContainerId() == null || deployment.getContainerId().isBlank()) {
            throw new ResourceNotFoundException("No container is associated with deployment: " + deploymentId, "CONTAINER_NOT_FOUND");
        }

        ContainerInfo info = dockerContainerService.inspectContainer(deployment.getContainerId());
        if (info == null) {
            return ContainerInfo.builder()
                    .containerId(deployment.getContainerId())
                    .status(ContainerStatus.UNKNOWN)
                    .hostPort(deployment.getContainerPort() != null ? deployment.getContainerPort() : 0)
                    .build();
        }

        if (info.getHostPort() <= 0 && deployment.getContainerPort() != null) {
            info.setHostPort(deployment.getContainerPort());
        }

        return info;
    }

    @Transactional(readOnly = true)
    public ContainerInfo restartDeploymentContainer(UUID userId, UUID deploymentId) {
        log.info("Restarting container for deployment id: {} user: {}", deploymentId, userId);
        Deployment deployment = getDeploymentForUser(userId, deploymentId);

        if (deployment.getContainerId() == null || deployment.getContainerId().isBlank()) {
            throw new ConflictException("Cannot restart: no container is associated with deployment: " + deploymentId, "NO_ACTIVE_CONTAINER");
        }

        dockerContainerService.restartContainer(deployment.getContainerId(), containerProperties.getStopTimeoutSeconds());
        return inspectDeploymentContainer(userId, deploymentId);
    }

    @Transactional(readOnly = true)
    public ContainerInfo stopDeploymentContainer(UUID userId, UUID deploymentId) {
        log.info("Stopping container for deployment id: {} user: {}", deploymentId, userId);
        Deployment deployment = getDeploymentForUser(userId, deploymentId);

        if (deployment.getContainerId() == null || deployment.getContainerId().isBlank()) {
            throw new ConflictException("Cannot stop: no container is associated with deployment: " + deploymentId, "NO_ACTIVE_CONTAINER");
        }

        dockerContainerService.stopContainer(deployment.getContainerId(), containerProperties.getStopTimeoutSeconds());
        return inspectDeploymentContainer(userId, deploymentId);
    }

    @Transactional(readOnly = true)
    public ContainerLogsResponse getDeploymentContainerLogs(UUID userId, UUID deploymentId, int lines) {
        log.debug("Fetching container logs for deployment id: {} lines: {} user: {}", deploymentId, lines, userId);
        Deployment deployment = getDeploymentForUser(userId, deploymentId);

        if (deployment.getContainerId() == null || deployment.getContainerId().isBlank()) {
            throw new ResourceNotFoundException("No container is associated with deployment: " + deploymentId, "CONTAINER_NOT_FOUND");
        }

        int clampedLines = Math.max(1, Math.min(lines, 2000));
        String rawLogs = dockerContainerService.getContainerLogs(deployment.getContainerId(), clampedLines);

        return ContainerLogsResponse.builder()
                .deploymentId(deploymentId)
                .containerId(deployment.getContainerId())
                .lines(clampedLines)
                .logs(rawLogs)
                .build();
    }

    private Deployment getDeploymentForUser(UUID userId, UUID deploymentId) {
        return deploymentRepository.findByIdAndUserId(deploymentId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Deployment not found with id: " + deploymentId));
    }
}

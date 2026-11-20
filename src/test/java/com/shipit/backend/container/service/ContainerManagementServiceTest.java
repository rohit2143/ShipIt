package com.shipit.backend.container.service;

import com.shipit.backend.common.exception.ConflictException;
import com.shipit.backend.common.exception.ResourceNotFoundException;
import com.shipit.backend.container.config.ContainerProperties;
import com.shipit.backend.container.dto.ContainerLogsResponse;
import com.shipit.backend.container.model.ContainerInfo;
import com.shipit.backend.container.model.ContainerStatus;
import com.shipit.backend.deployment.entity.Deployment;
import com.shipit.backend.deployment.entity.DeploymentStatus;
import com.shipit.backend.deployment.repository.DeploymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ContainerManagementServiceTest {

    @Mock
    private DeploymentRepository deploymentRepository;

    @Mock
    private DockerContainerService dockerContainerService;

    @Mock
    private ContainerProperties containerProperties;

    @InjectMocks
    private ContainerManagementService managementService;

    private UUID userId;
    private UUID deploymentId;
    private String containerId;
    private Deployment sampleDeployment;
    private ContainerInfo sampleContainerInfo;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        deploymentId = UUID.randomUUID();
        containerId = "c1234567890abcdef";

        sampleDeployment = Deployment.builder()
                .id(deploymentId)
                .containerId(containerId)
                .containerPort(10050)
                .status(DeploymentStatus.SUCCESS)
                .build();

        sampleContainerInfo = ContainerInfo.builder()
                .containerId(containerId)
                .status(ContainerStatus.RUNNING)
                .hostPort(10050)
                .containerPort(8080)
                .running(true)
                .build();

        lenient().when(containerProperties.getStopTimeoutSeconds()).thenReturn(10);
    }

    @Test
    @DisplayName("inspectDeploymentContainer - Successfully inspects running container")
    void inspectDeploymentContainer_Success() {
        when(deploymentRepository.findByIdAndUserId(deploymentId, userId)).thenReturn(Optional.of(sampleDeployment));
        when(dockerContainerService.inspectContainer(containerId)).thenReturn(sampleContainerInfo);

        ContainerInfo result = managementService.inspectDeploymentContainer(userId, deploymentId);

        assertThat(result).isNotNull();
        assertThat(result.getContainerId()).isEqualTo(containerId);
        assertThat(result.getStatus()).isEqualTo(ContainerStatus.RUNNING);
        assertThat(result.getHostPort()).isEqualTo(10050);
        verify(dockerContainerService).inspectContainer(containerId);
    }

    @Test
    @DisplayName("inspectDeploymentContainer - Deployment has no container throws ResourceNotFoundException")
    void inspectDeploymentContainer_NoContainer_ThrowsNotFound() {
        sampleDeployment.setContainerId(null);
        when(deploymentRepository.findByIdAndUserId(deploymentId, userId)).thenReturn(Optional.of(sampleDeployment));

        assertThatThrownBy(() -> managementService.inspectDeploymentContainer(userId, deploymentId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("No container is associated");
    }

    @Test
    @DisplayName("inspectDeploymentContainer - Docker inspect returns null returns UNKNOWN container info")
    void inspectDeploymentContainer_DockerReturnsNull_ReturnsUnknown() {
        when(deploymentRepository.findByIdAndUserId(deploymentId, userId)).thenReturn(Optional.of(sampleDeployment));
        when(dockerContainerService.inspectContainer(containerId)).thenReturn(null);

        ContainerInfo result = managementService.inspectDeploymentContainer(userId, deploymentId);

        assertThat(result).isNotNull();
        assertThat(result.getContainerId()).isEqualTo(containerId);
        assertThat(result.getStatus()).isEqualTo(ContainerStatus.UNKNOWN);
        assertThat(result.getHostPort()).isEqualTo(10050);
    }

    @Test
    @DisplayName("restartDeploymentContainer - Successfully restarts container")
    void restartDeploymentContainer_Success() {
        when(deploymentRepository.findByIdAndUserId(deploymentId, userId)).thenReturn(Optional.of(sampleDeployment));
        when(dockerContainerService.inspectContainer(containerId)).thenReturn(sampleContainerInfo);

        ContainerInfo result = managementService.restartDeploymentContainer(userId, deploymentId);

        assertThat(result).isNotNull();
        verify(dockerContainerService).restartContainer(containerId, 10);
        verify(dockerContainerService).inspectContainer(containerId);
    }

    @Test
    @DisplayName("restartDeploymentContainer - No container on deployment throws ConflictException")
    void restartDeploymentContainer_NoContainer_ThrowsConflict() {
        sampleDeployment.setContainerId(null);
        when(deploymentRepository.findByIdAndUserId(deploymentId, userId)).thenReturn(Optional.of(sampleDeployment));

        assertThatThrownBy(() -> managementService.restartDeploymentContainer(userId, deploymentId))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Cannot restart: no container is associated");
    }

    @Test
    @DisplayName("stopDeploymentContainer - Successfully stops container")
    void stopDeploymentContainer_Success() {
        when(deploymentRepository.findByIdAndUserId(deploymentId, userId)).thenReturn(Optional.of(sampleDeployment));
        ContainerInfo stoppedInfo = ContainerInfo.builder()
                .containerId(containerId)
                .status(ContainerStatus.STOPPED)
                .running(false)
                .build();
        when(dockerContainerService.inspectContainer(containerId)).thenReturn(stoppedInfo);

        ContainerInfo result = managementService.stopDeploymentContainer(userId, deploymentId);

        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(ContainerStatus.STOPPED);
        verify(dockerContainerService).stopContainer(containerId, 10);
    }

    @Test
    @DisplayName("stopDeploymentContainer - No container throws ConflictException")
    void stopDeploymentContainer_NoContainer_ThrowsConflict() {
        sampleDeployment.setContainerId(null);
        when(deploymentRepository.findByIdAndUserId(deploymentId, userId)).thenReturn(Optional.of(sampleDeployment));

        assertThatThrownBy(() -> managementService.stopDeploymentContainer(userId, deploymentId))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Cannot stop: no container is associated");
    }

    @Test
    @DisplayName("getDeploymentContainerLogs - Successfully returns live container logs")
    void getDeploymentContainerLogs_Success() {
        when(deploymentRepository.findByIdAndUserId(deploymentId, userId)).thenReturn(Optional.of(sampleDeployment));
        when(dockerContainerService.getContainerLogs(containerId, 100)).thenReturn("Application started on port 8080\n");

        ContainerLogsResponse response = managementService.getDeploymentContainerLogs(userId, deploymentId, 100);

        assertThat(response).isNotNull();
        assertThat(response.getDeploymentId()).isEqualTo(deploymentId);
        assertThat(response.getContainerId()).isEqualTo(containerId);
        assertThat(response.getLines()).isEqualTo(100);
        assertThat(response.getLogs()).isEqualTo("Application started on port 8080\n");
    }

    @Test
    @DisplayName("getDeploymentContainerLogs - Lines clamped to maximum 2000")
    void getDeploymentContainerLogs_ClampedLines() {
        when(deploymentRepository.findByIdAndUserId(deploymentId, userId)).thenReturn(Optional.of(sampleDeployment));
        when(dockerContainerService.getContainerLogs(containerId, 2000)).thenReturn("Lots of logs\n");

        ContainerLogsResponse response = managementService.getDeploymentContainerLogs(userId, deploymentId, 5000);

        assertThat(response.getLines()).isEqualTo(2000);
        verify(dockerContainerService).getContainerLogs(containerId, 2000);
    }

    @Test
    @DisplayName("getDeploymentContainerLogs - No container throws ResourceNotFoundException")
    void getDeploymentContainerLogs_NoContainer_ThrowsNotFound() {
        sampleDeployment.setContainerId(null);
        when(deploymentRepository.findByIdAndUserId(deploymentId, userId)).thenReturn(Optional.of(sampleDeployment));

        assertThatThrownBy(() -> managementService.getDeploymentContainerLogs(userId, deploymentId, 100))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("No container is associated");
    }

    @Test
    @DisplayName("Ownership check - Unknown or unauthorized deployment throws ResourceNotFoundException")
    void getDeploymentForUser_NotFound() {
        when(deploymentRepository.findByIdAndUserId(deploymentId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> managementService.inspectDeploymentContainer(userId, deploymentId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Deployment not found");
    }
}

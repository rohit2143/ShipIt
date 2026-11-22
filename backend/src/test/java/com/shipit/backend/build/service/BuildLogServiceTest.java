package com.shipit.backend.build.service;

import com.shipit.backend.build.dto.BuildLogsResponse;
import com.shipit.backend.common.exception.ResourceNotFoundException;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BuildLogServiceTest {

    @Mock
    private DeploymentRepository deploymentRepository;

    @InjectMocks
    private BuildLogService buildLogService;

    private UUID userId;
    private UUID deploymentId;
    private Deployment sampleDeployment;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        deploymentId = UUID.randomUUID();

        sampleDeployment = Deployment.builder()
                .id(deploymentId)
                .status(DeploymentStatus.SUCCESS)
                .build();

        buildLogService.clear();
    }

    @Test
    @DisplayName("storeLogs and getLogs - Successfully stores and retrieves logs with line count")
    void storeAndGetLogs_Success() {
        when(deploymentRepository.findByIdAndUserId(deploymentId, userId)).thenReturn(Optional.of(sampleDeployment));

        String logs = "Line 1: Cloning...\nLine 2: Building...\nLine 3: Success!\n";
        buildLogService.storeLogs(deploymentId, logs);

        BuildLogsResponse response = buildLogService.getLogs(userId, deploymentId);

        assertThat(response).isNotNull();
        assertThat(response.getDeploymentId()).isEqualTo(deploymentId);
        assertThat(response.getLogs()).isEqualTo(logs);
        assertThat(response.getLineCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("storeLogs - Null deploymentId does not throw or store")
    void storeLogs_NullDeploymentId_NoOp() {
        buildLogService.storeLogs(null, "some log text");
        assertThat(buildLogService.hasLogs(null)).isFalse();
    }

    @Test
    @DisplayName("getLogs - Deployment not found throws ResourceNotFoundException")
    void getLogs_DeploymentNotFound_ThrowsException() {
        when(deploymentRepository.findByIdAndUserId(deploymentId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> buildLogService.getLogs(userId, deploymentId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Deployment not found with id: " + deploymentId);
    }

    @Test
    @DisplayName("getLogs - Null userId or deploymentId throws IllegalArgumentException")
    void getLogs_NullArguments_ThrowsException() {
        assertThatThrownBy(() -> buildLogService.getLogs(null, deploymentId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("userId must not be null");

        assertThatThrownBy(() -> buildLogService.getLogs(userId, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("deploymentId must not be null");
    }

    @Test
    @DisplayName("getLogs - When no logs stored and deployment is QUEUED, returns queued placeholder")
    void getLogs_QueuedNoLogs_ReturnsQueuedPlaceholder() {
        sampleDeployment.setStatus(DeploymentStatus.QUEUED);
        when(deploymentRepository.findByIdAndUserId(deploymentId, userId)).thenReturn(Optional.of(sampleDeployment));

        BuildLogsResponse response = buildLogService.getLogs(userId, deploymentId);

        assertThat(response.getLogs()).contains("queued");
        assertThat(response.getLineCount()).isGreaterThan(0);
    }

    @Test
    @DisplayName("getLogs - When no logs stored and deployment is FAILED, returns no logs placeholder")
    void getLogs_FailedNoLogs_ReturnsPlaceholder() {
        sampleDeployment.setStatus(DeploymentStatus.FAILED);
        when(deploymentRepository.findByIdAndUserId(deploymentId, userId)).thenReturn(Optional.of(sampleDeployment));

        BuildLogsResponse response = buildLogService.getLogs(userId, deploymentId);

        assertThat(response.getLogs()).contains("No build logs recorded");
    }

    @Test
    @DisplayName("hasLogs and getRawLogs - Return expected state")
    void hasLogsAndGetRawLogs() {
        assertThat(buildLogService.hasLogs(deploymentId)).isFalse();
        assertThat(buildLogService.getRawLogs(deploymentId)).isEmpty();

        buildLogService.storeLogs(deploymentId, "Sample log content");

        assertThat(buildLogService.hasLogs(deploymentId)).isTrue();
        assertThat(buildLogService.getRawLogs(deploymentId)).contains("Sample log content");
    }

    @Test
    @DisplayName("clear - Removes all stored logs")
    void clear_RemovesAllLogs() {
        buildLogService.storeLogs(deploymentId, "Log line");
        assertThat(buildLogService.hasLogs(deploymentId)).isTrue();

        buildLogService.clear();
        assertThat(buildLogService.hasLogs(deploymentId)).isFalse();
    }

    @Test
    @DisplayName("LRU eviction - Cache drops oldest entries when exceeding MAX_STORED_LOGS")
    void lruEviction_ExceedingLimit() {
        UUID firstId = UUID.randomUUID();
        buildLogService.storeLogs(firstId, "Log entry 0");

        // Insert 100 more items (total 101)
        for (int i = 1; i <= BuildLogService.MAX_STORED_LOGS; i++) {
            buildLogService.storeLogs(UUID.randomUUID(), "Log entry " + i);
        }

        // First item should have been evicted
        assertThat(buildLogService.hasLogs(firstId)).isFalse();
    }
}

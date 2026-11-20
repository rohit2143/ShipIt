package com.shipit.backend.log.service;

import com.shipit.backend.build.logging.LogSource;
import com.shipit.backend.common.exception.ResourceNotFoundException;
import com.shipit.backend.deployment.entity.Deployment;
import com.shipit.backend.deployment.entity.DeploymentStatus;
import com.shipit.backend.deployment.repository.DeploymentRepository;
import com.shipit.backend.log.broadcaster.DeploymentLogBroadcaster;
import com.shipit.backend.log.dto.DeploymentLogDto;
import com.shipit.backend.log.dto.DeploymentLogsPageResponse;
import com.shipit.backend.log.entity.DeploymentLog;
import com.shipit.backend.log.entity.DeploymentStage;
import com.shipit.backend.log.repository.DeploymentLogRepository;
import com.shipit.backend.log.security.LogRedactor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeploymentLogServiceTest {

    @Mock
    private DeploymentLogRepository logRepository;

    @Mock
    private DeploymentRepository deploymentRepository;

    @Mock
    private LogRedactor logRedactor;

    @Mock
    private DeploymentLogBroadcaster broadcaster;

    @InjectMocks
    private DeploymentLogService logService;

    private UUID userId;
    private UUID deploymentId;
    private Deployment sampleDeployment;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        deploymentId = UUID.randomUUID();
        sampleDeployment = Deployment.builder()
                .id(deploymentId)
                .status(DeploymentStatus.BUILDING)
                .build();
    }

    @Test
    @DisplayName("appendLog - Redacts secrets, assigns sequence number, saves to repo, and broadcasts")
    void appendLog_Success() {
        when(deploymentRepository.getReferenceById(deploymentId)).thenReturn(sampleDeployment);
        when(logRepository.findMaxSequenceNumberByDeploymentId(deploymentId)).thenReturn(0L);
        when(logRedactor.redact(eq("Cloning repo with ghp_12345"), any())).thenReturn("Cloning repo with [REDACTED]");
        when(logRepository.save(any(DeploymentLog.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DeploymentLogDto dto = logService.appendLog(
                deploymentId,
                DeploymentStage.CLONING,
                LogSource.SYSTEM,
                "Cloning repo with ghp_12345",
                List.of("ghp_12345")
        );

        assertThat(dto).isNotNull();
        assertThat(dto.getSequenceNumber()).isEqualTo(1L);
        assertThat(dto.getMessage()).isEqualTo("Cloning repo with [REDACTED]");
        assertThat(dto.getStage()).isEqualTo(DeploymentStage.CLONING);
        assertThat(dto.getSource()).isEqualTo(LogSource.SYSTEM);

        verify(broadcaster).broadcastLog(eq(deploymentId), any(DeploymentLogDto.class));
    }

    @Test
    @DisplayName("appendLogs - Batch saves multiple log lines with monotonic sequence numbers")
    void appendLogs_Batch() {
        when(deploymentRepository.getReferenceById(deploymentId)).thenReturn(sampleDeployment);
        when(logRepository.findMaxSequenceNumberByDeploymentId(deploymentId)).thenReturn(10L);
        when(logRedactor.redact(anyString(), any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(logRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        List<String> rawLogs = List.of("Line 1", "Line 2", "Line 3");

        List<DeploymentLogDto> dtos = logService.appendLogs(
                deploymentId,
                DeploymentStage.BUILDING,
                LogSource.STDOUT,
                rawLogs,
                null
        );

        assertThat(dtos).hasSize(3);
        assertThat(dtos.get(0).getSequenceNumber()).isEqualTo(11L);
        assertThat(dtos.get(1).getSequenceNumber()).isEqualTo(12L);
        assertThat(dtos.get(2).getSequenceNumber()).isEqualTo(13L);

        verify(broadcaster, times(3)).broadcastLog(eq(deploymentId), any(DeploymentLogDto.class));
    }

    @Test
    @DisplayName("recordStatusChange - Broadcasts status and sends completion on terminal state")
    void recordStatusChange_Terminal() {
        logService.recordStatusChange(deploymentId, DeploymentStatus.SUCCESS, "Deployment succeeded");

        verify(broadcaster).broadcastStatus(deploymentId, "SUCCESS", "Deployment succeeded");
        verify(broadcaster).broadcastComplete(deploymentId, "SUCCESS");
    }

    @Test
    @DisplayName("getLogs - Successfully retrieves paginated logs when user has access")
    void getLogs_Success() {
        when(deploymentRepository.findByIdAndUserId(deploymentId, userId)).thenReturn(Optional.of(sampleDeployment));

        DeploymentLog entity1 = DeploymentLog.builder()
                .sequenceNumber(1L)
                .stage(DeploymentStage.BUILDING)
                .source(LogSource.STDOUT)
                .message("Log 1")
                .timestamp(Instant.now())
                .build();

        when(logRepository.findByDeploymentIdOrderBySequenceNumberAsc(eq(deploymentId), any(Pageable.class)))
                .thenReturn(List.of(entity1));
        when(logRepository.countByDeploymentId(deploymentId)).thenReturn(1L);
        when(logRepository.findMaxSequenceNumberByDeploymentId(deploymentId)).thenReturn(1L);

        DeploymentLogsPageResponse response = logService.getLogs(userId, deploymentId, null, null, 0, 10);

        assertThat(response.getDeploymentId()).isEqualTo(deploymentId);
        assertThat(response.getLogs()).hasSize(1);
        assertThat(response.getLogs().get(0).getMessage()).isEqualTo("Log 1");
        assertThat(response.getTotalLines()).isEqualTo(1L);
        assertThat(response.getMaxSequenceNumber()).isEqualTo(1L);
        assertThat(response.isHasMore()).isFalse();
    }

    @Test
    @DisplayName("getLogs - Throws ResourceNotFoundException when deployment does not exist or user lacks access")
    void getLogs_Unauthorized() {
        when(deploymentRepository.findByIdAndUserId(deploymentId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> logService.getLogs(userId, deploymentId, null, null, 0, 10))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("getFullLogsAsString - Returns formatted text block of all deployment logs")
    void getFullLogsAsString_Success() {
        when(deploymentRepository.findByIdAndUserId(deploymentId, userId)).thenReturn(Optional.of(sampleDeployment));

        Instant timestamp = Instant.parse("2026-11-20T10:00:00Z");
        DeploymentLog log1 = DeploymentLog.builder()
                .sequenceNumber(1L)
                .stage(DeploymentStage.CLONING)
                .source(LogSource.SYSTEM)
                .message("Clone started")
                .timestamp(timestamp)
                .build();
        DeploymentLog log2 = DeploymentLog.builder()
                .sequenceNumber(2L)
                .stage(DeploymentStage.BUILDING)
                .source(LogSource.STDOUT)
                .message("Building maven")
                .timestamp(timestamp)
                .build();

        when(logRepository.findByDeploymentIdOrderBySequenceNumberAsc(deploymentId)).thenReturn(List.of(log1, log2));

        String text = logService.getFullLogsAsString(userId, deploymentId);

        assertThat(text).contains("[2026-11-20T10:00:00Z] [CLONING] [SYSTEM] Clone started");
        assertThat(text).contains("[2026-11-20T10:00:00Z] [BUILDING] [STDOUT] Building maven");
    }

    @Test
    @DisplayName("subscribeToStream - Connects to broadcaster when deployment is in progress")
    void subscribeToStream_Active() {
        sampleDeployment.setStatus(DeploymentStatus.BUILDING);
        when(deploymentRepository.findByIdAndUserId(deploymentId, userId)).thenReturn(Optional.of(sampleDeployment));

        SseEmitter mockEmitter = new SseEmitter();
        when(broadcaster.subscribe(deploymentId, null)).thenReturn(mockEmitter);

        SseEmitter emitter = logService.subscribeToStream(userId, deploymentId, null);

        assertThat(emitter).isSameAs(mockEmitter);
        verify(broadcaster).subscribe(deploymentId, null);
    }

    @Test
    @DisplayName("subscribeToStream - Replays from database and completes immediately when deployment is terminal")
    void subscribeToStream_Terminal() {
        sampleDeployment.setStatus(DeploymentStatus.SUCCESS);
        when(deploymentRepository.findByIdAndUserId(deploymentId, userId)).thenReturn(Optional.of(sampleDeployment));
        when(logRepository.findByDeploymentIdOrderBySequenceNumberAsc(deploymentId)).thenReturn(List.of());

        SseEmitter emitter = logService.subscribeToStream(userId, deploymentId, null);

        assertThat(emitter).isNotNull();
        verifyNoInteractions(broadcaster);
    }

    @Test
    @DisplayName("cleanupDeploymentLogs - Purges logs and broadcaster cache")
    void cleanupDeploymentLogs() {
        logService.cleanupDeploymentLogs(deploymentId);

        verify(logRepository).deleteByDeploymentId(deploymentId);
        verify(broadcaster).cleanupDeployment(deploymentId);
    }
}

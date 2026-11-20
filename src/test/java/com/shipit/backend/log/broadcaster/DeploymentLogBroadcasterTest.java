package com.shipit.backend.log.broadcaster;

import com.shipit.backend.build.logging.LogSource;
import com.shipit.backend.log.config.LogProperties;
import com.shipit.backend.log.dto.DeploymentLogDto;
import com.shipit.backend.log.entity.DeploymentStage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeploymentLogBroadcasterTest {

    private LogProperties logProperties;
    private DeploymentLogBroadcaster broadcaster;
    private UUID deploymentId;

    @BeforeEach
    void setUp() {
        logProperties = new LogProperties();
        logProperties.setMaxReplayLines(5);
        broadcaster = new DeploymentLogBroadcaster(logProperties);
        deploymentId = UUID.randomUUID();
    }

    @Test
    @DisplayName("subscribe - Successfully creates emitter and registers subscriber")
    void subscribe_Success() {
        SseEmitter emitter = broadcaster.subscribe(deploymentId, null);

        assertThat(emitter).isNotNull();
        assertThat(broadcaster.getSubscriberCount(deploymentId)).isEqualTo(1);
    }

    @Test
    @DisplayName("subscribe - Throws on null deploymentId")
    void subscribe_NullDeploymentId() {
        assertThatThrownBy(() -> broadcaster.subscribe(null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("broadcastLog - Stores in replay backlog and maintains ring buffer limit")
    void broadcastLog_ReplayBufferBounded() {
        for (int i = 1; i <= 7; i++) {
            DeploymentLogDto dto = DeploymentLogDto.builder()
                    .sequenceNumber(i)
                    .stage(DeploymentStage.BUILDING)
                    .source(LogSource.STDOUT)
                    .message("Step " + i)
                    .timestamp(Instant.now())
                    .build();
            broadcaster.broadcastLog(deploymentId, dto);
        }

        List<DeploymentLogDto> backlog = broadcaster.getReplayBacklog(deploymentId);
        // Capped to maxReplayLines = 5
        assertThat(backlog).hasSize(5);
        assertThat(backlog.get(0).getSequenceNumber()).isEqualTo(3L);
        assertThat(backlog.get(4).getSequenceNumber()).isEqualTo(7L);
    }

    @Test
    @DisplayName("subscribe - Replays prior backlog to newly connected subscriber")
    void subscribe_ReplaysBacklog() {
        DeploymentLogDto log1 = DeploymentLogDto.builder()
                .sequenceNumber(1L)
                .stage(DeploymentStage.CLONING)
                .source(LogSource.SYSTEM)
                .message("Clone completed")
                .timestamp(Instant.now())
                .build();
        DeploymentLogDto log2 = DeploymentLogDto.builder()
                .sequenceNumber(2L)
                .stage(DeploymentStage.BUILDING)
                .source(LogSource.STDOUT)
                .message("Building container image")
                .timestamp(Instant.now())
                .build();

        broadcaster.broadcastLog(deploymentId, log1);
        broadcaster.broadcastLog(deploymentId, log2);

        // Subscribing after logs were already produced
        SseEmitter emitter = broadcaster.subscribe(deploymentId, 1L);
        assertThat(emitter).isNotNull();
        assertThat(broadcaster.getSubscriberCount(deploymentId)).isEqualTo(1);
    }

    @Test
    @DisplayName("broadcastStatus & broadcastComplete - Completes emitters and clears subscribers")
    void broadcastStatusAndComplete() {
        broadcaster.subscribe(deploymentId, null);
        assertThat(broadcaster.getSubscriberCount(deploymentId)).isEqualTo(1);

        broadcaster.broadcastStatus(deploymentId, "BUILDING", "Starting Maven compilation");
        broadcaster.broadcastComplete(deploymentId, "SUCCESS");

        assertThat(broadcaster.getSubscriberCount(deploymentId)).isEqualTo(0);
    }

    @Test
    @DisplayName("cleanupDeployment - Removes all state for deployment")
    void cleanupDeployment() {
        broadcaster.subscribe(deploymentId, null);
        broadcaster.broadcastLog(deploymentId, DeploymentLogDto.builder().sequenceNumber(1L).build());

        broadcaster.cleanupDeployment(deploymentId);

        assertThat(broadcaster.getSubscriberCount(deploymentId)).isEqualTo(0);
        assertThat(broadcaster.getReplayBacklog(deploymentId)).isEmpty();
    }
}

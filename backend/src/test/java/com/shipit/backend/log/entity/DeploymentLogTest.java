package com.shipit.backend.log.entity;

import com.shipit.backend.build.logging.LogSource;
import com.shipit.backend.deployment.entity.Deployment;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DeploymentLogTest {

    @Test
    @DisplayName("DeploymentLog - Builder and formatting test")
    void deploymentLog_BuilderAndFormat() {
        UUID logId = UUID.randomUUID();
        Deployment deployment = Deployment.builder().id(UUID.randomUUID()).build();
        Instant now = Instant.parse("2026-11-20T12:00:00Z");

        DeploymentLog log = DeploymentLog.builder()
                .id(logId)
                .deployment(deployment)
                .sequenceNumber(42L)
                .stage(DeploymentStage.BUILDING)
                .source(LogSource.STDOUT)
                .message("Compiling 45 source files to /target/classes")
                .timestamp(now)
                .build();

        assertThat(log.getId()).isEqualTo(logId);
        assertThat(log.getDeployment()).isEqualTo(deployment);
        assertThat(log.getSequenceNumber()).isEqualTo(42L);
        assertThat(log.getStage()).isEqualTo(DeploymentStage.BUILDING);
        assertThat(log.getSource()).isEqualTo(LogSource.STDOUT);
        assertThat(log.getMessage()).isEqualTo("Compiling 45 source files to /target/classes");
        assertThat(log.getTimestamp()).isEqualTo(now);

        String formatted = log.toFormattedString();
        assertThat(formatted).isEqualTo("[2026-11-20T12:00:00Z] [BUILDING] [STDOUT] Compiling 45 source files to /target/classes");
    }

    @Test
    @DisplayName("DeploymentLog - PrePersist initializes timestamp if null")
    void deploymentLog_PrePersist() {
        DeploymentLog log = DeploymentLog.builder()
                .timestamp(null)
                .build();

        log.prePersist();
        assertThat(log.getTimestamp()).isNotNull();
    }
}

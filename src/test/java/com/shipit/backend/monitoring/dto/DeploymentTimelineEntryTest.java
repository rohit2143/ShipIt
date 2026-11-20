package com.shipit.backend.monitoring.dto;

import com.shipit.backend.deployment.entity.Deployment;
import com.shipit.backend.deployment.entity.DeploymentStatus;
import com.shipit.backend.deployment.entity.DeploymentTrigger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DeploymentTimelineEntryTest {

    @Test
    @DisplayName("fromEntity - Correctly maps fields and computes duration")
    void fromEntity_ComputesDuration() {
        Instant started = Instant.parse("2026-11-20T12:00:00Z");
        Instant finished = Instant.parse("2026-11-20T12:00:45Z"); // 45 seconds

        Deployment d = Deployment.builder()
                .id(UUID.randomUUID())
                .status(DeploymentStatus.SUCCESS)
                .trigger(DeploymentTrigger.MANUAL)
                .commitSha("abc1234")
                .commitBranch("main")
                .commitMessage("feat: adds metrics")
                .commitAuthor("developer")
                .dockerImageTag("shipit/app:abc1234")
                .startedAt(started)
                .finishedAt(finished)
                .containerPort(10050)
                .build();

        DeploymentTimelineEntry entry = DeploymentTimelineEntry.fromEntity(d);

        assertThat(entry.getDeploymentId()).isEqualTo(d.getId());
        assertThat(entry.getStatus()).isEqualTo(DeploymentStatus.SUCCESS);
        assertThat(entry.getTrigger()).isEqualTo(DeploymentTrigger.MANUAL);
        assertThat(entry.getCommitSha()).isEqualTo("abc1234");
        assertThat(entry.getDurationSeconds()).isEqualTo(45L);
        assertThat(entry.getContainerPort()).isEqualTo(10050);
    }

    @Test
    @DisplayName("fromEntity - Handles null dates without throwing")
    void fromEntity_NullDates() {
        Deployment d = Deployment.builder()
                .id(UUID.randomUUID())
                .status(DeploymentStatus.QUEUED)
                .build();

        DeploymentTimelineEntry entry = DeploymentTimelineEntry.fromEntity(d);

        assertThat(entry).isNotNull();
        assertThat(entry.getDurationSeconds()).isNull();
    }

    @Test
    @DisplayName("fromEntity - Returns null for null entity")
    void fromEntity_Null() {
        assertThat(DeploymentTimelineEntry.fromEntity(null)).isNull();
    }
}

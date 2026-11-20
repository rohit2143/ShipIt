package com.shipit.backend.log.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DeploymentStageTest {

    @Test
    @DisplayName("isBuildPhase - Correctly classifies build stages")
    void isBuildPhase_Classification() {
        assertThat(DeploymentStage.CLONING.isBuildPhase()).isTrue();
        assertThat(DeploymentStage.DETECTING.isBuildPhase()).isTrue();
        assertThat(DeploymentStage.BUILDING.isBuildPhase()).isTrue();

        assertThat(DeploymentStage.STARTING.isBuildPhase()).isFalse();
        assertThat(DeploymentStage.HEALTH_CHECKING.isBuildPhase()).isFalse();
        assertThat(DeploymentStage.CONTAINER.isBuildPhase()).isFalse();
        assertThat(DeploymentStage.SYSTEM.isBuildPhase()).isFalse();
    }

    @Test
    @DisplayName("isRuntimePhase - Correctly classifies runtime container stages")
    void isRuntimePhase_Classification() {
        assertThat(DeploymentStage.STARTING.isRuntimePhase()).isTrue();
        assertThat(DeploymentStage.HEALTH_CHECKING.isRuntimePhase()).isTrue();
        assertThat(DeploymentStage.CONTAINER.isRuntimePhase()).isTrue();

        assertThat(DeploymentStage.CLONING.isRuntimePhase()).isFalse();
        assertThat(DeploymentStage.DETECTING.isRuntimePhase()).isFalse();
        assertThat(DeploymentStage.BUILDING.isRuntimePhase()).isFalse();
        assertThat(DeploymentStage.SYSTEM.isRuntimePhase()).isFalse();
    }
}

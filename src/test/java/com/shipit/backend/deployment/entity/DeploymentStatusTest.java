package com.shipit.backend.deployment.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeploymentStatusTest {

    @Test
    @DisplayName("isTerminal returns true only for terminal states")
    void isTerminal_ReturnsTrueOnlyForTerminalStates() {
        assertThat(DeploymentStatus.SUCCESS.isTerminal()).isTrue();
        assertThat(DeploymentStatus.FAILED.isTerminal()).isTrue();
        assertThat(DeploymentStatus.CANCELLED.isTerminal()).isTrue();

        assertThat(DeploymentStatus.QUEUED.isTerminal()).isFalse();
        assertThat(DeploymentStatus.CLONING.isTerminal()).isFalse();
        assertThat(DeploymentStatus.DETECTING.isTerminal()).isFalse();
        assertThat(DeploymentStatus.BUILDING.isTerminal()).isFalse();
        assertThat(DeploymentStatus.STARTING.isTerminal()).isFalse();
        assertThat(DeploymentStatus.HEALTH_CHECKING.isTerminal()).isFalse();
    }

    @Test
    @DisplayName("isInProgress returns true only for non-terminal lifecycle states")
    void isInProgress_ReturnsTrueOnlyForInProgressStates() {
        assertThat(DeploymentStatus.QUEUED.isInProgress()).isTrue();
        assertThat(DeploymentStatus.CLONING.isInProgress()).isTrue();
        assertThat(DeploymentStatus.DETECTING.isInProgress()).isTrue();
        assertThat(DeploymentStatus.BUILDING.isInProgress()).isTrue();
        assertThat(DeploymentStatus.STARTING.isInProgress()).isTrue();
        assertThat(DeploymentStatus.HEALTH_CHECKING.isInProgress()).isTrue();

        assertThat(DeploymentStatus.SUCCESS.isInProgress()).isFalse();
        assertThat(DeploymentStatus.FAILED.isInProgress()).isFalse();
        assertThat(DeploymentStatus.CANCELLED.isInProgress()).isFalse();
    }

    @Test
    @DisplayName("canTransitionTo validates valid transitions from QUEUED")
    void canTransitionTo_FromQueued() {
        assertThat(DeploymentStatus.QUEUED.canTransitionTo(DeploymentStatus.CLONING)).isTrue();
        assertThat(DeploymentStatus.QUEUED.canTransitionTo(DeploymentStatus.CANCELLED)).isTrue();
        assertThat(DeploymentStatus.QUEUED.canTransitionTo(DeploymentStatus.FAILED)).isTrue();

        assertThat(DeploymentStatus.QUEUED.canTransitionTo(DeploymentStatus.BUILDING)).isFalse();
        assertThat(DeploymentStatus.QUEUED.canTransitionTo(DeploymentStatus.SUCCESS)).isFalse();
        assertThat(DeploymentStatus.QUEUED.canTransitionTo(null)).isFalse();
    }

    @Test
    @DisplayName("canTransitionTo validates valid sequential pipeline progression")
    void canTransitionTo_SequentialPipeline() {
        assertThat(DeploymentStatus.CLONING.canTransitionTo(DeploymentStatus.DETECTING)).isTrue();
        assertThat(DeploymentStatus.CLONING.canTransitionTo(DeploymentStatus.CANCELLED)).isTrue();
        assertThat(DeploymentStatus.CLONING.canTransitionTo(DeploymentStatus.FAILED)).isTrue();

        assertThat(DeploymentStatus.DETECTING.canTransitionTo(DeploymentStatus.BUILDING)).isTrue();
        assertThat(DeploymentStatus.DETECTING.canTransitionTo(DeploymentStatus.FAILED)).isTrue();
        assertThat(DeploymentStatus.DETECTING.canTransitionTo(DeploymentStatus.CANCELLED)).isFalse();

        assertThat(DeploymentStatus.BUILDING.canTransitionTo(DeploymentStatus.STARTING)).isTrue();
        assertThat(DeploymentStatus.BUILDING.canTransitionTo(DeploymentStatus.FAILED)).isTrue();

        assertThat(DeploymentStatus.STARTING.canTransitionTo(DeploymentStatus.HEALTH_CHECKING)).isTrue();
        assertThat(DeploymentStatus.STARTING.canTransitionTo(DeploymentStatus.FAILED)).isTrue();

        assertThat(DeploymentStatus.HEALTH_CHECKING.canTransitionTo(DeploymentStatus.SUCCESS)).isTrue();
        assertThat(DeploymentStatus.HEALTH_CHECKING.canTransitionTo(DeploymentStatus.FAILED)).isTrue();
    }

    @Test
    @DisplayName("Terminal states have no valid subsequent transitions")
    void terminalStates_HaveNoTransitions() {
        for (DeploymentStatus target : DeploymentStatus.values()) {
            assertThat(DeploymentStatus.SUCCESS.canTransitionTo(target)).isFalse();
            assertThat(DeploymentStatus.FAILED.canTransitionTo(target)).isFalse();
            assertThat(DeploymentStatus.CANCELLED.canTransitionTo(target)).isFalse();
        }
    }

    @Test
    @DisplayName("Deployment entity transitionTo updates status, startedAt and finishedAt appropriately")
    void deployment_TransitionTo_UpdatesTimestamps() {
        Deployment deployment = Deployment.builder()
                .status(DeploymentStatus.QUEUED)
                .queuedAt(Instant.now())
                .build();

        assertThat(deployment.getStartedAt()).isNull();
        assertThat(deployment.getFinishedAt()).isNull();

        // QUEUED -> CLONING
        deployment.transitionTo(DeploymentStatus.CLONING);
        assertThat(deployment.getStatus()).isEqualTo(DeploymentStatus.CLONING);
        assertThat(deployment.getStartedAt()).isNotNull();
        assertThat(deployment.getFinishedAt()).isNull();

        // CLONING -> DETECTING
        deployment.transitionTo(DeploymentStatus.DETECTING);
        assertThat(deployment.getStatus()).isEqualTo(DeploymentStatus.DETECTING);

        // DETECTING -> BUILDING
        deployment.transitionTo(DeploymentStatus.BUILDING);
        assertThat(deployment.getStatus()).isEqualTo(DeploymentStatus.BUILDING);

        // BUILDING -> STARTING
        deployment.transitionTo(DeploymentStatus.STARTING);
        assertThat(deployment.getStatus()).isEqualTo(DeploymentStatus.STARTING);

        // STARTING -> HEALTH_CHECKING
        deployment.transitionTo(DeploymentStatus.HEALTH_CHECKING);
        assertThat(deployment.getStatus()).isEqualTo(DeploymentStatus.HEALTH_CHECKING);

        // HEALTH_CHECKING -> SUCCESS (Terminal)
        deployment.transitionTo(DeploymentStatus.SUCCESS);
        assertThat(deployment.getStatus()).isEqualTo(DeploymentStatus.SUCCESS);
        assertThat(deployment.getFinishedAt()).isNotNull();

        // Attempting transition from terminal throws IllegalStateException
        assertThatThrownBy(() -> deployment.transitionTo(DeploymentStatus.FAILED))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Invalid deployment status transition from SUCCESS to FAILED");
    }

    @Test
    @DisplayName("Deployment cancel and markFailed behave as expected")
    void deployment_CancelAndMarkFailed() {
        Deployment queuedDep = Deployment.builder()
                .status(DeploymentStatus.QUEUED)
                .queuedAt(Instant.now())
                .build();

        queuedDep.cancel();
        assertThat(queuedDep.getStatus()).isEqualTo(DeploymentStatus.CANCELLED);
        assertThat(queuedDep.getFinishedAt()).isNotNull();

        Deployment buildingDep = Deployment.builder()
                .status(DeploymentStatus.BUILDING)
                .queuedAt(Instant.now())
                .startedAt(Instant.now())
                .build();

        String longError = "Error: ".repeat(250); // > 1000 chars
        buildingDep.markFailed(longError);
        assertThat(buildingDep.getStatus()).isEqualTo(DeploymentStatus.FAILED);
        assertThat(buildingDep.getFinishedAt()).isNotNull();
        assertThat(buildingDep.getErrorMessage()).hasSize(1000);
    }
}

package com.shipit.backend.job.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

class JobStatusTest {

    @Test
    @DisplayName("isTerminal returns true only for terminal states")
    void testIsTerminal() {
        assertThat(JobStatus.COMPLETED.isTerminal()).isTrue();
        assertThat(JobStatus.FAILED.isTerminal()).isTrue();
        assertThat(JobStatus.CANCELLED.isTerminal()).isTrue();

        assertThat(JobStatus.PENDING.isTerminal()).isFalse();
        assertThat(JobStatus.RUNNING.isTerminal()).isFalse();
    }

    @Test
    @DisplayName("isActive returns true only for PENDING and RUNNING")
    void testIsActive() {
        assertThat(JobStatus.PENDING.isActive()).isTrue();
        assertThat(JobStatus.RUNNING.isActive()).isTrue();

        assertThat(JobStatus.COMPLETED.isActive()).isFalse();
        assertThat(JobStatus.FAILED.isActive()).isFalse();
        assertThat(JobStatus.CANCELLED.isActive()).isFalse();
    }

    @Test
    @DisplayName("PENDING valid transitions: RUNNING, CANCELLED, FAILED")
    void testPendingTransitions() {
        assertThat(JobStatus.PENDING.canTransitionTo(JobStatus.RUNNING)).isTrue();
        assertThat(JobStatus.PENDING.canTransitionTo(JobStatus.CANCELLED)).isTrue();
        assertThat(JobStatus.PENDING.canTransitionTo(JobStatus.FAILED)).isTrue();

        assertThat(JobStatus.PENDING.canTransitionTo(JobStatus.COMPLETED)).isFalse();
        assertThat(JobStatus.PENDING.canTransitionTo(JobStatus.PENDING)).isFalse();
        assertThat(JobStatus.PENDING.canTransitionTo(null)).isFalse();
    }

    @Test
    @DisplayName("RUNNING valid transitions: COMPLETED, FAILED, CANCELLED, PENDING (retry)")
    void testRunningTransitions() {
        assertThat(JobStatus.RUNNING.canTransitionTo(JobStatus.COMPLETED)).isTrue();
        assertThat(JobStatus.RUNNING.canTransitionTo(JobStatus.FAILED)).isTrue();
        assertThat(JobStatus.RUNNING.canTransitionTo(JobStatus.CANCELLED)).isTrue();
        assertThat(JobStatus.RUNNING.canTransitionTo(JobStatus.PENDING)).isTrue();

        assertThat(JobStatus.RUNNING.canTransitionTo(JobStatus.RUNNING)).isFalse();
        assertThat(JobStatus.RUNNING.canTransitionTo(null)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = JobStatus.class, names = {"COMPLETED", "FAILED", "CANCELLED"})
    @DisplayName("Terminal states have zero valid outgoing transitions")
    void testTerminalStatesHaveNoOutgoingTransitions(JobStatus terminalStatus) {
        for (JobStatus target : JobStatus.values()) {
            assertThat(terminalStatus.canTransitionTo(target))
                    .as("%s should not be able to transition to %s", terminalStatus, target)
                    .isFalse();
        }
        assertThat(terminalStatus.canTransitionTo(null)).isFalse();
    }
}

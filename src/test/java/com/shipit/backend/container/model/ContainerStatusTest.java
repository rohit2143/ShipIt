package com.shipit.backend.container.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ContainerStatusTest {

    @Test
    @DisplayName("isActive returns true only for active container lifecycle states")
    void testIsActive() {
        assertThat(ContainerStatus.RUNNING.isActive()).isTrue();
        assertThat(ContainerStatus.HEALTHY.isActive()).isTrue();
        assertThat(ContainerStatus.STARTING.isActive()).isTrue();

        assertThat(ContainerStatus.UNHEALTHY.isActive()).isFalse();
        assertThat(ContainerStatus.STOPPED.isActive()).isFalse();
        assertThat(ContainerStatus.EXITED.isActive()).isFalse();
        assertThat(ContainerStatus.UNKNOWN.isActive()).isFalse();
    }
}

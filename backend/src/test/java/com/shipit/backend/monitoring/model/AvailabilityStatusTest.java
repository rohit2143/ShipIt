package com.shipit.backend.monitoring.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AvailabilityStatusTest {

    @Test
    @DisplayName("isAvailable - Correctly flags accessible statuses")
    void isAvailable_Check() {
        assertThat(AvailabilityStatus.HEALTHY.isAvailable()).isTrue();
        assertThat(AvailabilityStatus.DEGRADED.isAvailable()).isTrue();

        assertThat(AvailabilityStatus.UNHEALTHY.isAvailable()).isFalse();
        assertThat(AvailabilityStatus.STOPPED.isAvailable()).isFalse();
        assertThat(AvailabilityStatus.UNKNOWN.isAvailable()).isFalse();
    }

    @Test
    @DisplayName("requiresAttention - Correctly flags degraded or unhealthy statuses")
    void requiresAttention_Check() {
        assertThat(AvailabilityStatus.DEGRADED.requiresAttention()).isTrue();
        assertThat(AvailabilityStatus.UNHEALTHY.requiresAttention()).isTrue();

        assertThat(AvailabilityStatus.HEALTHY.requiresAttention()).isFalse();
        assertThat(AvailabilityStatus.STOPPED.requiresAttention()).isFalse();
        assertThat(AvailabilityStatus.UNKNOWN.requiresAttention()).isFalse();
    }
}

package com.shipit.backend.monitoring.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MonitoringPropertiesTest {

    @Test
    @DisplayName("MonitoringProperties - Default values and custom mutators")
    void properties_DefaultsAndMutators() {
        MonitoringProperties props = new MonitoringProperties();

        assertThat(props.getHealthCheckIntervalSeconds()).isEqualTo(30);
        assertThat(props.getHealthCheckTimeoutSeconds()).isEqualTo(5);
        assertThat(props.getMaxUnhealthyThreshold()).isEqualTo(3);
        assertThat(props.getStatsTimeoutSeconds()).isEqualTo(5);
        assertThat(props.getHistoryRetentionDays()).isEqualTo(30);

        props.setHealthCheckIntervalSeconds(15);
        props.setHealthCheckTimeoutSeconds(10);
        props.setMaxUnhealthyThreshold(5);
        props.setStatsTimeoutSeconds(8);
        props.setHistoryRetentionDays(60);

        assertThat(props.getHealthCheckIntervalSeconds()).isEqualTo(15);
        assertThat(props.getHealthCheckTimeoutSeconds()).isEqualTo(10);
        assertThat(props.getMaxUnhealthyThreshold()).isEqualTo(5);
        assertThat(props.getStatsTimeoutSeconds()).isEqualTo(8);
        assertThat(props.getHistoryRetentionDays()).isEqualTo(60);
    }
}

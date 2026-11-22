package com.shipit.backend.container.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ContainerPropertiesTest {

    @Test
    @DisplayName("ContainerProperties has valid default values aligned with resource requirements")
    void defaultValues() {
        ContainerProperties properties = new ContainerProperties();

        assertThat(properties.getMinPort()).isEqualTo(10000);
        assertThat(properties.getMaxPort()).isEqualTo(20000);
        assertThat(properties.getMemoryLimit()).isEqualTo("512m");
        assertThat(properties.getCpuLimit()).isEqualTo("0.5");
        assertThat(properties.getHealthCheckTimeoutSeconds()).isEqualTo(60);
        assertThat(properties.getHealthCheckIntervalSeconds()).isEqualTo(2);
        assertThat(properties.getHealthCheckPath()).isEqualTo("/");
        assertThat(properties.getStopTimeoutSeconds()).isEqualTo(10);
        assertThat(properties.getRestartPolicy()).isEqualTo("unless-stopped");
    }

    @Test
    @DisplayName("ContainerProperties allows overriding values")
    void customValues() {
        ContainerProperties properties = new ContainerProperties();
        properties.setMinPort(12000);
        properties.setMaxPort(13000);
        properties.setMemoryLimit("1g");
        properties.setCpuLimit("1.0");
        properties.setHealthCheckTimeoutSeconds(90);
        properties.setHealthCheckIntervalSeconds(5);
        properties.setHealthCheckPath("/healthz");
        properties.setStopTimeoutSeconds(15);
        properties.setRestartPolicy("always");

        assertThat(properties.getMinPort()).isEqualTo(12000);
        assertThat(properties.getMaxPort()).isEqualTo(13000);
        assertThat(properties.getMemoryLimit()).isEqualTo("1g");
        assertThat(properties.getCpuLimit()).isEqualTo("1.0");
        assertThat(properties.getHealthCheckTimeoutSeconds()).isEqualTo(90);
        assertThat(properties.getHealthCheckIntervalSeconds()).isEqualTo(5);
        assertThat(properties.getHealthCheckPath()).isEqualTo("/healthz");
        assertThat(properties.getStopTimeoutSeconds()).isEqualTo(15);
        assertThat(properties.getRestartPolicy()).isEqualTo("always");
    }
}

package com.shipit.backend.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ShipitPropertiesTest {

    @Test
    @DisplayName("Should initialize with sensible default values")
    void shouldInitializeWithSensibleDefaults() {
        ShipitProperties properties = new ShipitProperties();

        assertThat(properties.getApp().getName()).isEqualTo("ShipIt");
        assertThat(properties.getApp().getBaseUrl()).isEqualTo("http://localhost:8080");
        assertThat(properties.getCors().getAllowedOrigins())
                .containsExactlyInAnyOrder("http://localhost:3000", "http://localhost:5173");
        assertThat(properties.getCors().getAllowedMethods())
                .contains("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");
        assertThat(properties.getCors().isAllowCredentials()).isTrue();
        assertThat(properties.getCors().getMaxAge()).isEqualTo(3600);
    }
}

package com.shipit.backend.build.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BuildPropertiesTest {

    @Test
    @DisplayName("BuildProperties initializes with production-ready defaults")
    void testDefaultValues() {
        BuildProperties properties = new BuildProperties();

        assertThat(properties.getWorkspaceBaseDir()).isEqualTo("/tmp/shipit-builds");
        assertThat(properties.getTimeoutSeconds()).isEqualTo(600L);
        assertThat(properties.getMaxLogLines()).isEqualTo(5000);
        assertThat(properties.getMaxLineLength()).isEqualTo(1024);
        assertThat(properties.getDefaultJdkImage()).isEqualTo("eclipse-temurin:21-jdk-alpine");
        assertThat(properties.getDefaultJreImage()).isEqualTo("eclipse-temurin:21-jre-alpine");
    }

    @Test
    @DisplayName("BuildProperties allows modifying properties")
    void testSetters() {
        BuildProperties properties = new BuildProperties();
        properties.setWorkspaceBaseDir("/var/shipit/scratch");
        properties.setTimeoutSeconds(900L);
        properties.setMaxLogLines(10000);
        properties.setMaxLineLength(2048);
        properties.setDefaultJdkImage("maven:3.9-eclipse-temurin-21");
        properties.setDefaultJreImage("amazoncorretto:21-alpine");

        assertThat(properties.getWorkspaceBaseDir()).isEqualTo("/var/shipit/scratch");
        assertThat(properties.getTimeoutSeconds()).isEqualTo(900L);
        assertThat(properties.getMaxLogLines()).isEqualTo(10000);
        assertThat(properties.getMaxLineLength()).isEqualTo(2048);
        assertThat(properties.getDefaultJdkImage()).isEqualTo("maven:3.9-eclipse-temurin-21");
        assertThat(properties.getDefaultJreImage()).isEqualTo("amazoncorretto:21-alpine");
    }
}

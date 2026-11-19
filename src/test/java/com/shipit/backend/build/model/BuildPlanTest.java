package com.shipit.backend.build.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class BuildPlanTest {

    @Test
    @DisplayName("BuildPlan isValid returns true when all mandatory fields are populated")
    void testValidBuildPlan() {
        BuildPlan plan = BuildPlan.builder()
                .projectId(UUID.randomUUID())
                .deploymentId(UUID.randomUUID())
                .projectType(ProjectType.MAVEN_SPRING_BOOT)
                .rootDirectory("/")
                .dockerfilePath("/Dockerfile")
                .customDockerfile(false)
                .buildCommand("./mvnw clean package -DskipTests")
                .port(8080)
                .imageTag("shipit/sample-service:abc1234")
                .jdkImage("eclipse-temurin:21-jdk-alpine")
                .jreImage("eclipse-temurin:21-jre-alpine")
                .build();

        assertThat(plan.isValid()).isTrue();
        assertThat(plan.getCreatedAt()).isNotNull();
        assertThat(plan.isCustomDockerfile()).isFalse();
    }

    @Test
    @DisplayName("BuildPlan isValid returns false for missing or invalid parameters")
    void testInvalidBuildPlan() {
        UUID projectId = UUID.randomUUID();
        UUID deploymentId = UUID.randomUUID();

        // Null projectId
        assertThat(BuildPlan.builder()
                .deploymentId(deploymentId)
                .projectType(ProjectType.MAVEN_SPRING_BOOT)
                .port(8080)
                .imageTag("shipit/app:tag")
                .build().isValid()).isFalse();

        // Null deploymentId
        assertThat(BuildPlan.builder()
                .projectId(projectId)
                .projectType(ProjectType.MAVEN_SPRING_BOOT)
                .port(8080)
                .imageTag("shipit/app:tag")
                .build().isValid()).isFalse();

        // UNKNOWN or null project type
        assertThat(BuildPlan.builder()
                .projectId(projectId)
                .deploymentId(deploymentId)
                .projectType(ProjectType.UNKNOWN)
                .port(8080)
                .imageTag("shipit/app:tag")
                .build().isValid()).isFalse();

        assertThat(BuildPlan.builder()
                .projectId(projectId)
                .deploymentId(deploymentId)
                .projectType(null)
                .port(8080)
                .imageTag("shipit/app:tag")
                .build().isValid()).isFalse();

        // Blank or null image tag
        assertThat(BuildPlan.builder()
                .projectId(projectId)
                .deploymentId(deploymentId)
                .projectType(ProjectType.MAVEN_SPRING_BOOT)
                .port(8080)
                .imageTag("")
                .build().isValid()).isFalse();

        // Invalid port numbers
        assertThat(BuildPlan.builder()
                .projectId(projectId)
                .deploymentId(deploymentId)
                .projectType(ProjectType.MAVEN_SPRING_BOOT)
                .port(0)
                .imageTag("shipit/app:tag")
                .build().isValid()).isFalse();

        assertThat(BuildPlan.builder()
                .projectId(projectId)
                .deploymentId(deploymentId)
                .projectType(ProjectType.MAVEN_SPRING_BOOT)
                .port(70000)
                .imageTag("shipit/app:tag")
                .build().isValid()).isFalse();
    }

    @Test
    @DisplayName("ProjectType enum has expected values")
    void testProjectTypeEnum() {
        assertThat(ProjectType.valueOf("MAVEN_SPRING_BOOT")).isEqualTo(ProjectType.MAVEN_SPRING_BOOT);
        assertThat(ProjectType.valueOf("DOCKERFILE_CUSTOM")).isEqualTo(ProjectType.DOCKERFILE_CUSTOM);
        assertThat(ProjectType.valueOf("UNKNOWN")).isEqualTo(ProjectType.UNKNOWN);
    }
}

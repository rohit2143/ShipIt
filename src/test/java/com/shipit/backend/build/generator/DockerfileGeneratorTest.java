package com.shipit.backend.build.generator;

import com.shipit.backend.build.exception.BuildException;
import com.shipit.backend.build.logging.BuildLogBuffer;
import com.shipit.backend.build.model.BuildPlan;
import com.shipit.backend.build.model.ProjectType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DockerfileGeneratorTest {

    @TempDir
    Path tempDir;

    private DockerfileGenerator generator;
    private BuildLogBuffer logBuffer;

    @BeforeEach
    void setUp() {
        generator = new DockerfileGenerator();
        logBuffer = new BuildLogBuffer(100, 200);
    }

    @Test
    @DisplayName("generateDockerfile synthesizes multi-stage Dockerfile for Maven Spring Boot")
    void testGenerateMavenDockerfile() throws IOException {
        BuildPlan plan = BuildPlan.builder()
                .projectId(UUID.randomUUID())
                .deploymentId(UUID.randomUUID())
                .projectType(ProjectType.MAVEN_SPRING_BOOT)
                .rootDirectory("/")
                .port(8080)
                .imageTag("shipit/app:tag")
                .jdkImage("eclipse-temurin:21-jdk-alpine")
                .jreImage("eclipse-temurin:21-jre-alpine")
                .build();

        Path generatedPath = generator.generateDockerfile(tempDir, plan, logBuffer);

        assertThat(generatedPath).isNotNull();
        assertThat(Files.exists(generatedPath)).isTrue();
        assertThat(generatedPath.getFileName().toString()).isEqualTo("Dockerfile.shipit");
        assertThat(plan.getDockerfilePath()).isEqualTo(generatedPath.toAbsolutePath().toString());

        String content = Files.readString(generatedPath);
        assertThat(content).contains("FROM maven:3.9.9-eclipse-temurin-21-alpine AS builder");
        assertThat(content).contains("COPY pom.xml .");
        assertThat(content).contains("RUN mvn clean package -DskipTests -B");
        assertThat(content).contains("FROM eclipse-temurin:21-jre-alpine AS runner");
        assertThat(content).contains("USER shipituser");
        assertThat(content).contains("EXPOSE 8080");
        assertThat(content).contains("ENTRYPOINT [\"java\", \"-jar\", \"/app/app.jar\"]");
    }

    @Test
    @DisplayName("generateDockerfile incorporates custom build command and custom port")
    void testGenerateWithCustomBuildCommandAndPort() throws IOException {
        BuildPlan plan = BuildPlan.builder()
                .projectId(UUID.randomUUID())
                .deploymentId(UUID.randomUUID())
                .projectType(ProjectType.MAVEN_SPRING_BOOT)
                .buildCommand("./mvnw clean package -Pproduction -DskipTests")
                .port(9090)
                .imageTag("shipit/app:prod")
                .build();

        Path generatedPath = generator.generateDockerfile(tempDir, plan, logBuffer);

        String content = Files.readString(generatedPath);
        assertThat(content).contains("RUN ./mvnw clean package -Pproduction -DskipTests");
        assertThat(content).contains("EXPOSE 9090");
    }

    @Test
    @DisplayName("generateDockerfile leaves custom Dockerfile intact without overwriting")
    void testCustomDockerfileIntact() throws IOException {
        Path existingDockerfile = tempDir.resolve("Dockerfile");
        String originalContent = "FROM custom-base:latest\nENTRYPOINT [\"./run.sh\"]\n";
        Files.writeString(existingDockerfile, originalContent);

        BuildPlan plan = BuildPlan.builder()
                .projectId(UUID.randomUUID())
                .deploymentId(UUID.randomUUID())
                .projectType(ProjectType.DOCKERFILE_CUSTOM)
                .customDockerfile(true)
                .dockerfilePath(existingDockerfile.toAbsolutePath().toString())
                .port(8080)
                .imageTag("shipit/custom:tag")
                .build();

        Path resolvedPath = generator.generateDockerfile(tempDir, plan, logBuffer);

        assertThat(resolvedPath).isEqualTo(existingDockerfile);
        assertThat(Files.readString(existingDockerfile)).isEqualTo(originalContent);
    }

    @Test
    @DisplayName("generateDockerfile throws BuildException when custom Dockerfile is missing")
    void testMissingCustomDockerfile() {
        BuildPlan plan = BuildPlan.builder()
                .projectId(UUID.randomUUID())
                .deploymentId(UUID.randomUUID())
                .projectType(ProjectType.DOCKERFILE_CUSTOM)
                .customDockerfile(true)
                .dockerfilePath(tempDir.resolve("Dockerfile.notfound").toString())
                .port(8080)
                .imageTag("shipit/custom:tag")
                .build();

        assertThatThrownBy(() -> generator.generateDockerfile(tempDir, plan, logBuffer))
                .isInstanceOf(BuildException.class)
                .hasMessageContaining("not found");
    }

    @Test
    @DisplayName("generateDockerfile throws BuildException for unsupported project type")
    void testUnsupportedProjectType() {
        BuildPlan plan = BuildPlan.builder()
                .projectId(UUID.randomUUID())
                .deploymentId(UUID.randomUUID())
                .projectType(ProjectType.UNKNOWN)
                .build();

        assertThatThrownBy(() -> generator.generateDockerfile(tempDir, plan, logBuffer))
                .isInstanceOf(BuildException.class)
                .hasMessageContaining("Cannot generate Dockerfile for project type: UNKNOWN");
    }

    @Test
    @DisplayName("generateDockerfile validates non-null parameters")
    void testNullValidation() {
        assertThatThrownBy(() -> generator.generateDockerfile(null, BuildPlan.builder().build(), logBuffer))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> generator.generateDockerfile(tempDir, null, logBuffer))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

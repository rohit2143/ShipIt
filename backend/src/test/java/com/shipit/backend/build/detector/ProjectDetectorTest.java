package com.shipit.backend.build.detector;

import com.shipit.backend.build.config.BuildProperties;
import com.shipit.backend.build.exception.BuildException;
import com.shipit.backend.build.logging.BuildLogBuffer;
import com.shipit.backend.build.model.BuildPlan;
import com.shipit.backend.build.model.ProjectType;
import com.shipit.backend.deployment.entity.Deployment;
import com.shipit.backend.deployment.entity.DeploymentStatus;
import com.shipit.backend.project.entity.Project;
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

class ProjectDetectorTest {

    @TempDir
    Path tempDir;

    private BuildProperties buildProperties;
    private ProjectDetector detector;
    private Project project;
    private Deployment deployment;
    private BuildLogBuffer logBuffer;

    @BeforeEach
    void setUp() {
        buildProperties = new BuildProperties();
        detector = new ProjectDetector(buildProperties);
        logBuffer = new BuildLogBuffer(100, 200);

        project = Project.builder()
                .id(UUID.randomUUID())
                .name("Sample App")
                .normalizedName("sample-app")
                .rootDirectory("/")
                .port(8080)
                .build();

        deployment = Deployment.builder()
                .id(UUID.randomUUID())
                .project(project)
                .status(DeploymentStatus.QUEUED)
                .commitBranch("main")
                .commitSha("abcdef1234567890abcdef1234567890abcdef12")
                .build();
    }

    @Test
    @DisplayName("detect recognizes Maven Spring Boot project from pom.xml")
    void testDetect_MavenSpringBoot() throws IOException {
        Files.writeString(tempDir.resolve("pom.xml"), "<project></project>");

        BuildPlan plan = detector.detect(tempDir, deployment, logBuffer);

        assertThat(plan).isNotNull();
        assertThat(plan.getProjectType()).isEqualTo(ProjectType.MAVEN_SPRING_BOOT);
        assertThat(plan.isCustomDockerfile()).isFalse();
        assertThat(plan.getDockerfilePath()).endsWith("Dockerfile.shipit");
        assertThat(plan.getPort()).isEqualTo(8080);
        assertThat(plan.getImageTag()).isEqualTo("shipit/sample-app:abcdef1");
        assertThat(plan.isValid()).isTrue();
    }

    @Test
    @DisplayName("detect recognizes custom Dockerfile when present")
    void testDetect_CustomDockerfile() throws IOException {
        Files.writeString(tempDir.resolve("Dockerfile"), "FROM openjdk:21\nEXPOSE 9000\n");

        BuildPlan plan = detector.detect(tempDir, deployment, logBuffer);

        assertThat(plan).isNotNull();
        assertThat(plan.getProjectType()).isEqualTo(ProjectType.DOCKERFILE_CUSTOM);
        assertThat(plan.isCustomDockerfile()).isTrue();
        assertThat(plan.getDockerfilePath()).isEqualTo(tempDir.resolve("Dockerfile").toAbsolutePath().toString());
        assertThat(plan.getPort()).isEqualTo(9000); // parsed from EXPOSE 9000
    }

    @Test
    @DisplayName("detect prioritizes custom Dockerfile if both Dockerfile and pom.xml exist")
    void testDetect_DockerfilePriority() throws IOException {
        Files.writeString(tempDir.resolve("Dockerfile"), "FROM alpine\n");
        Files.writeString(tempDir.resolve("pom.xml"), "<project></project>");

        BuildPlan plan = detector.detect(tempDir, deployment, logBuffer);

        assertThat(plan.getProjectType()).isEqualTo(ProjectType.DOCKERFILE_CUSTOM);
        assertThat(plan.isCustomDockerfile()).isTrue();
    }

    @Test
    @DisplayName("detect parses server.port from application.properties")
    void testDetect_PortFromApplicationProperties() throws IOException {
        Files.writeString(tempDir.resolve("pom.xml"), "<project></project>");
        Path resDir = tempDir.resolve("src/main/resources");
        Files.createDirectories(resDir);
        Files.writeString(resDir.resolve("application.properties"), "server.port=9191\nspring.application.name=demo\n");

        BuildPlan plan = detector.detect(tempDir, deployment, logBuffer);

        assertThat(plan.getPort()).isEqualTo(9191);
    }

    @Test
    @DisplayName("detect parses port from application.yml")
    void testDetect_PortFromApplicationYml() throws IOException {
        Files.writeString(tempDir.resolve("pom.xml"), "<project></project>");
        Path resDir = tempDir.resolve("src/main/resources");
        Files.createDirectories(resDir);
        Files.writeString(resDir.resolve("application.yml"), "server:\n  port: 7070\n");

        BuildPlan plan = detector.detect(tempDir, deployment, logBuffer);

        assertThat(plan.getPort()).isEqualTo(7070);
    }

    @Test
    @DisplayName("detect uses explicit project port when configured to non-default value")
    void testDetect_ExplicitProjectPort() throws IOException {
        project.setPort(8088);
        Files.writeString(tempDir.resolve("pom.xml"), "<project></project>");
        Path resDir = tempDir.resolve("src/main/resources");
        Files.createDirectories(resDir);
        Files.writeString(resDir.resolve("application.properties"), "server.port=9191\n");

        BuildPlan plan = detector.detect(tempDir, deployment, logBuffer);

        assertThat(plan.getPort()).isEqualTo(8088);
    }

    @Test
    @DisplayName("detect resolves nested rootDirectory properly")
    void testDetect_NestedRootDirectory() throws IOException {
        Path subModule = tempDir.resolve("services/backend-api");
        Files.createDirectories(subModule);
        Files.writeString(subModule.resolve("pom.xml"), "<project></project>");

        project.setRootDirectory("/services/backend-api");

        BuildPlan plan = detector.detect(tempDir, deployment, logBuffer);

        assertThat(plan.getProjectType()).isEqualTo(ProjectType.MAVEN_SPRING_BOOT);
        assertThat(plan.getRootDirectory()).isEqualTo("/services/backend-api");
    }

    @Test
    @DisplayName("detect resolves rootDirectory configured as 'backend'")
    void testDetect_ConfiguredRootDirectory_Backend() throws IOException {
        Path backendDir = tempDir.resolve("backend");
        Files.createDirectories(backendDir);
        Files.writeString(backendDir.resolve("pom.xml"), "<project></project>");

        project.setRootDirectory("backend");

        BuildPlan plan = detector.detect(tempDir, deployment, logBuffer);

        assertThat(plan.getProjectType()).isEqualTo(ProjectType.MAVEN_SPRING_BOOT);
        assertThat(plan.getRootDirectory()).isEqualTo("backend");
        assertThat(plan.getDockerfilePath()).isEqualTo(backendDir.resolve("Dockerfile.shipit").toAbsolutePath().toString());
    }

    @Test
    @DisplayName("detect resolves rootDirectory with leading and trailing slashes")
    void testDetect_ConfiguredRootDirectory_SlashesAndWhitespace() throws IOException {
        Path backendDir = tempDir.resolve("backend");
        Files.createDirectories(backendDir);
        Files.writeString(backendDir.resolve("pom.xml"), "<project></project>");

        project.setRootDirectory("  /backend/  ");

        BuildPlan plan = detector.detect(tempDir, deployment, logBuffer);

        assertThat(plan.getProjectType()).isEqualTo(ProjectType.MAVEN_SPRING_BOOT);
        assertThat(plan.getDockerfilePath()).isEqualTo(backendDir.resolve("Dockerfile.shipit").toAbsolutePath().toString());
    }

    @Test
    @DisplayName("detect resolves rootDirectory pointing to custom Dockerfile")
    void testDetect_ConfiguredRootDirectory_CustomDockerfile() throws IOException {
        Path backendDir = tempDir.resolve("backend");
        Files.createDirectories(backendDir);
        Files.writeString(backendDir.resolve("Dockerfile"), "FROM openjdk:21\nEXPOSE 8080\n");

        project.setRootDirectory("backend");

        BuildPlan plan = detector.detect(tempDir, deployment, logBuffer);

        assertThat(plan.getProjectType()).isEqualTo(ProjectType.DOCKERFILE_CUSTOM);
        assertThat(plan.isCustomDockerfile()).isTrue();
        assertThat(plan.getDockerfilePath()).isEqualTo(backendDir.resolve("Dockerfile").toAbsolutePath().toString());
    }

    @Test
    @DisplayName("detect throws BuildException on path traversal attempt in rootDirectory")
    void testDetect_PathTraversal() {
        project.setRootDirectory("../../etc/passwd");

        assertThatThrownBy(() -> detector.detect(tempDir, deployment, logBuffer))
                .isInstanceOf(BuildException.class)
                .hasMessageContaining("path traversal");
    }

    @Test
    @DisplayName("detect throws BuildException when rootDirectory does not exist")
    void testDetect_MissingRootDirectory() {
        project.setRootDirectory("non-existent-folder");

        assertThatThrownBy(() -> detector.detect(tempDir, deployment, logBuffer))
                .isInstanceOf(BuildException.class)
                .hasMessageContaining("does not exist");
    }

    @Test
    @DisplayName("detect throws BuildException for unsupported project layout")
    void testDetect_UnsupportedLayout() {
        // Empty directory - no pom.xml, no Dockerfile
        assertThatThrownBy(() -> detector.detect(tempDir, deployment, logBuffer))
                .isInstanceOf(BuildException.class)
                .hasMessageContaining("Unsupported project layout");
    }
}

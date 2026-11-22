package com.shipit.backend.build.workspace;

import com.shipit.backend.build.config.BuildProperties;
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

class WorkspaceManagerTest {

    @TempDir
    Path tempDir;

    private BuildProperties properties;
    private WorkspaceManager workspaceManager;

    @BeforeEach
    void setUp() {
        properties = new BuildProperties();
        properties.setWorkspaceBaseDir(tempDir.toString());
        workspaceManager = new WorkspaceManager(properties);
    }

    @Test
    @DisplayName("createWorkspace creates isolated source directory for deployment")
    void testCreateWorkspace() {
        UUID deploymentId = UUID.randomUUID();

        Path sourceDir = workspaceManager.createWorkspace(deploymentId);

        assertThat(sourceDir).isNotNull();
        assertThat(Files.exists(sourceDir)).isTrue();
        assertThat(Files.isDirectory(sourceDir)).isTrue();
        assertThat(sourceDir.toString()).contains(deploymentId.toString());
        assertThat(sourceDir.endsWith("source")).isTrue();
    }

    @Test
    @DisplayName("getWorkspacePath and getDeploymentBasePath return consistent paths")
    void testGetPaths() {
        UUID deploymentId = UUID.randomUUID();

        Path basePath = workspaceManager.getDeploymentBasePath(deploymentId);
        Path sourcePath = workspaceManager.getWorkspacePath(deploymentId);

        assertThat(sourcePath).isEqualTo(basePath.resolve("source"));
    }

    @Test
    @DisplayName("deleteWorkspace recursively removes files and subdirectories")
    void testDeleteWorkspace() throws IOException {
        UUID deploymentId = UUID.randomUUID();
        Path sourceDir = workspaceManager.createWorkspace(deploymentId);

        // Add dummy files
        Path file1 = sourceDir.resolve("pom.xml");
        Files.writeString(file1, "<project></project>");
        Path nested = sourceDir.resolve("src/main/java");
        Files.createDirectories(nested);
        Path file2 = nested.resolve("App.java");
        Files.writeString(file2, "class App {}");

        assertThat(Files.exists(file1)).isTrue();
        assertThat(Files.exists(file2)).isTrue();

        workspaceManager.deleteWorkspace(deploymentId);

        Path basePath = workspaceManager.getDeploymentBasePath(deploymentId);
        assertThat(Files.exists(basePath)).isFalse();
    }

    @Test
    @DisplayName("deleteWorkspace does not fail if directory does not exist")
    void testDeleteNonExistentWorkspace() {
        UUID deploymentId = UUID.randomUUID();
        workspaceManager.deleteWorkspace(deploymentId);
        // Should complete without throwing exceptions
    }

    @Test
    @DisplayName("Methods validate non-null deployment ID")
    void testNullValidation() {
        assertThatThrownBy(() -> workspaceManager.createWorkspace(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> workspaceManager.getDeploymentBasePath(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

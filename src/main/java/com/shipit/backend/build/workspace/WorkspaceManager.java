package com.shipit.backend.build.workspace;

import com.shipit.backend.build.config.BuildProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.UUID;

/**
 * Manages the lifecycle of ephemeral disk workspaces where repositories are cloned
 * and built for each deployment.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkspaceManager {

    private final BuildProperties buildProperties;

    /**
     * Resolves and creates an isolated scratch workspace directory for the deployment:
     * &lt;workspaceBaseDir&gt;/&lt;deploymentId&gt;/source
     */
    public Path createWorkspace(UUID deploymentId) {
        if (deploymentId == null) {
            throw new IllegalArgumentException("deploymentId must not be null");
        }

        Path sourcePath = getWorkspacePath(deploymentId);
        try {
            Files.createDirectories(sourcePath);
            log.debug("Created scratch workspace directory at: {}", sourcePath);
            return sourcePath;
        } catch (IOException e) {
            log.error("Failed to create workspace directory: {}", sourcePath, e);
            throw new IllegalStateException("Failed to create build workspace directory: " + e.getMessage(), e);
        }
    }

    /**
     * Returns the target source directory for the given deployment.
     */
    public Path getWorkspacePath(UUID deploymentId) {
        return getDeploymentBasePath(deploymentId).resolve("source").normalize();
    }

    /**
     * Returns the base directory for the given deployment.
     */
    public Path getDeploymentBasePath(UUID deploymentId) {
        if (deploymentId == null) {
            throw new IllegalArgumentException("deploymentId must not be null");
        }
        Path basePath = Paths.get(buildProperties.getWorkspaceBaseDir()).toAbsolutePath().normalize();
        Path deploymentPath = basePath.resolve(deploymentId.toString()).normalize();

        // Path traversal guard
        if (!deploymentPath.startsWith(basePath)) {
            throw new SecurityException("Potential path traversal attack detected for deployment: " + deploymentId);
        }

        return deploymentPath;
    }

    /**
     * Recursively and safely deletes the deployment workspace directory tree.
     */
    public void deleteWorkspace(UUID deploymentId) {
        if (deploymentId == null) {
            return;
        }

        Path deploymentPath = getDeploymentBasePath(deploymentId);
        if (!Files.exists(deploymentPath)) {
            return;
        }

        try {
            Files.walkFileTree(deploymentPath, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.deleteIfExists(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.deleteIfExists(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
            log.info("Successfully cleaned up build workspace for deployment: {}", deploymentId);
        } catch (IOException e) {
            log.warn("Failed to clean up build workspace for deployment {}: {}", deploymentId, e.getMessage());
        }
    }
}

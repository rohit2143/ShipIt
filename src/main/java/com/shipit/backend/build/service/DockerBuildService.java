package com.shipit.backend.build.service;

import com.shipit.backend.build.config.BuildProperties;
import com.shipit.backend.build.exception.BuildException;
import com.shipit.backend.build.logging.BuildLogBuffer;
import com.shipit.backend.build.logging.LogSource;
import com.shipit.backend.build.model.BuildPlan;
import com.shipit.backend.build.model.BuildResult;
import com.shipit.backend.build.process.ProcessExecutor;
import com.shipit.backend.build.process.ProcessResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Service responsible for executing container image builds via Docker, capturing build output streams,
 * enforcing execution timeouts, and recording image metadata.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DockerBuildService {

    private final ProcessExecutor processExecutor;
    private final BuildProperties buildProperties;

    /**
     * Builds a Docker image using the specified build context and BuildPlan.
     *
     * @param appRoot Application root directory containing the build context.
     * @param buildPlan Configuration plan specifying Dockerfile, image tag, and ports.
     * @param logBuffer Buffer for capturing stdout/stderr build logs in real time.
     * @return Execution metrics and image metadata upon build completion.
     */
    public BuildResult buildImage(Path appRoot, BuildPlan buildPlan, BuildLogBuffer logBuffer) {
        if (appRoot == null || !Files.exists(appRoot) || !Files.isDirectory(appRoot)) {
            throw new IllegalArgumentException("Build context directory must exist: " + appRoot);
        }
        if (buildPlan == null || !buildPlan.isValid()) {
            throw new IllegalArgumentException("BuildPlan must be non-null and valid");
        }

        Path dockerfilePath = Path.of(buildPlan.getDockerfilePath());
        if (!Files.exists(dockerfilePath)) {
            throw new BuildException("Dockerfile not found at: " + dockerfilePath, "DOCKERFILE_NOT_FOUND");
        }

        String imageTag = buildPlan.getImageTag();
        long timeoutSeconds = buildProperties.getTimeoutSeconds();

        log.info("Starting Docker build for deployment {} (imageTag: {}, context: {})",
                buildPlan.getDeploymentId(), imageTag, appRoot);

        if (logBuffer != null) {
            logBuffer.append(LogSource.SYSTEM, String.format("Starting Docker image build for tag '%s'...", imageTag));
        }

        Instant startedAt = Instant.now();

        List<String> command = List.of(
                "docker", "build",
                "--file", dockerfilePath.toAbsolutePath().toString(),
                "--tag", imageTag,
                "."
        );

        ProcessResult result = processExecutor.execute(
                command,
                appRoot,
                logBuffer,
                timeoutSeconds,
                Set.of()
        );

        Instant finishedAt = Instant.now();
        long durationSeconds = Duration.between(startedAt, finishedAt).toSeconds();

        if (result.isTimedOut()) {
            String errorMsg = String.format("Docker image build timed out after %d seconds", timeoutSeconds);
            log.error("Deployment {} build timeout: {}", buildPlan.getDeploymentId(), errorMsg);
            if (logBuffer != null) {
                logBuffer.append(LogSource.STDERR, errorMsg);
            }
            throw new BuildException(errorMsg, "BUILD_TIMEOUT");
        }

        if (!result.isSuccess()) {
            String errorMsg = String.format("Docker image build failed with exit code %d", result.getExitCode());
            log.error("Deployment {} build failed: {}", buildPlan.getDeploymentId(), errorMsg);
            if (logBuffer != null) {
                logBuffer.append(LogSource.STDERR, errorMsg);
            }
            throw new BuildException(errorMsg, "DOCKER_BUILD_FAILED");
        }

        if (logBuffer != null) {
            logBuffer.append(LogSource.SYSTEM, String.format("Successfully built Docker image '%s' in %ds", imageTag, durationSeconds));
        }
        log.info("Deployment {} successfully built Docker image '{}' in {}s",
                buildPlan.getDeploymentId(), imageTag, durationSeconds);

        return BuildResult.builder()
                .imageTag(imageTag)
                .imageId(imageTag)
                .startedAt(startedAt)
                .finishedAt(finishedAt)
                .durationSeconds(durationSeconds)
                .success(true)
                .build();
    }

    /**
     * Checks if a Docker image with the specified tag exists locally.
     */
    public boolean imageExists(String imageTag) {
        if (imageTag == null || imageTag.isBlank()) {
            return false;
        }
        List<String> command = List.of("docker", "image", "inspect", imageTag);
        ProcessResult result = processExecutor.execute(command, null, null, 10, Set.of());
        return result.isSuccess();
    }

    /**
     * Removes a local Docker image by tag, cleaning up disk resources.
     */
    public void removeImage(String imageTag, BuildLogBuffer logBuffer) {
        if (imageTag == null || imageTag.isBlank()) {
            return;
        }
        log.info("Removing Docker image: {}", imageTag);
        if (logBuffer != null) {
            logBuffer.append(LogSource.SYSTEM, "Removing Docker image: " + imageTag);
        }
        List<String> command = List.of("docker", "rmi", "-f", imageTag);
        processExecutor.execute(command, null, logBuffer, 30, Set.of());
    }
}

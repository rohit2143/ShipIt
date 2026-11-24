package com.shipit.backend.build.detector;

import com.shipit.backend.build.config.BuildProperties;
import com.shipit.backend.build.exception.BuildException;
import com.shipit.backend.build.logging.BuildLogBuffer;
import com.shipit.backend.build.logging.LogSource;
import com.shipit.backend.build.model.BuildPlan;
import com.shipit.backend.build.model.ProjectType;
import com.shipit.backend.deployment.entity.Deployment;
import com.shipit.backend.project.entity.Project;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Inspects a cloned repository workspace to detect project layout, packaging mechanism,
 * build commands, and target runtime ports.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProjectDetector {

    private static final Pattern PROPERTIES_PORT_PATTERN = Pattern.compile("^\\s*server\\.port\\s*=\\s*(\\d+)", Pattern.MULTILINE);
    private static final Pattern YAML_PORT_PATTERN = Pattern.compile("(?:server\\s*:\\s*\\n(?:\\s+.*\\n)*)?\\s+port\\s*:\\s*(\\d+)", Pattern.MULTILINE);
    private static final Pattern DOCKERFILE_EXPOSE_PATTERN = Pattern.compile("^\\s*EXPOSE\\s+(\\d+)", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);

    private final BuildProperties buildProperties;

    /**
     * Inspects the workspace and constructs an actionable BuildPlan for the deployment.
     */
    public BuildPlan detect(Path workspaceRoot, Deployment deployment, BuildLogBuffer logBuffer) {
        if (workspaceRoot == null || !Files.exists(workspaceRoot)) {
            throw new IllegalArgumentException("Workspace root directory does not exist: " + workspaceRoot);
        }
        if (deployment == null || deployment.getProject() == null) {
            throw new IllegalArgumentException("Deployment and associated project must not be null");
        }

        Project project = deployment.getProject();
        Path appRoot = resolveApplicationRoot(workspaceRoot, project.getRootDirectory());

        log.debug("Analyzing project layout at: {}", appRoot);
        if (logBuffer != null) {
            logBuffer.append(LogSource.SYSTEM, "Analyzing project layout at " + appRoot + "...");
        }

        // 1. Check for custom Dockerfile
        Path dockerfilePath = appRoot.resolve("Dockerfile");
        if (Files.exists(dockerfilePath) && Files.isRegularFile(dockerfilePath)) {
            int port = detectDockerfilePort(dockerfilePath, project.getPort());
            String imageTag = generateImageTag(project, deployment);

            log.info("Detected custom Dockerfile for deployment {} at {}", deployment.getId(), dockerfilePath);
            if (logBuffer != null) {
                logBuffer.append(LogSource.SYSTEM, "Detected existing custom Dockerfile in repository.");
            }

            return BuildPlan.builder()
                    .projectId(project.getId())
                    .deploymentId(deployment.getId())
                    .projectType(ProjectType.DOCKERFILE_CUSTOM)
                    .rootDirectory(project.getRootDirectory())
                    .dockerfilePath(dockerfilePath.toAbsolutePath().toString())
                    .customDockerfile(true)
                    .buildCommand(project.getBuildCommand())
                    .port(port)
                    .imageTag(imageTag)
                    .jdkImage(buildProperties.getDefaultJdkImage())
                    .jreImage(buildProperties.getDefaultJreImage())
                    .build();
        }

        // 2. Check for Maven Spring Boot project
        Path pomPath = appRoot.resolve("pom.xml");
        if (Files.exists(pomPath) && Files.isRegularFile(pomPath)) {
            int port = detectMavenPort(appRoot, project.getPort());
            String imageTag = generateImageTag(project, deployment);

            log.info("Detected Maven Spring Boot project for deployment {} at {}", deployment.getId(), pomPath);
            if (logBuffer != null) {
                logBuffer.append(LogSource.SYSTEM, "Detected Maven Spring Boot project (pom.xml).");
            }

            return BuildPlan.builder()
                    .projectId(project.getId())
                    .deploymentId(deployment.getId())
                    .projectType(ProjectType.MAVEN_SPRING_BOOT)
                    .rootDirectory(project.getRootDirectory())
                    .dockerfilePath(appRoot.resolve("Dockerfile.shipit").toAbsolutePath().toString())
                    .customDockerfile(false)
                    .buildCommand(project.getBuildCommand())
                    .port(port)
                    .imageTag(imageTag)
                    .jdkImage(buildProperties.getDefaultJdkImage())
                    .jreImage(buildProperties.getDefaultJreImage())
                    .build();
        }

        // 3. Unsupported project layout
        String errorMsg = String.format("Unsupported project layout: no pom.xml or Dockerfile detected in %s",
                project.getRootDirectory() != null ? project.getRootDirectory() : "/");
        log.warn("Deployment {} failed detection: {}", deployment.getId(), errorMsg);
        if (logBuffer != null) {
            logBuffer.append(LogSource.STDERR, errorMsg);
        }
        throw new BuildException(errorMsg, "UNSUPPORTED_PROJECT_TYPE");
    }

    /**
     * Resolves and validates the relative application root directory inside the cloned workspace.
     */
    public Path resolveApplicationRoot(Path workspaceRoot, String configuredRootDir) {
        Path normalizedWorkspace = workspaceRoot.toAbsolutePath().normalize();

        if (configuredRootDir == null || configuredRootDir.isBlank()) {
            return normalizedWorkspace;
        }

        String cleaned = configuredRootDir.trim();
        while (cleaned.startsWith("/")) {
            cleaned = cleaned.substring(1).trim();
        }
        while (cleaned.endsWith("/")) {
            cleaned = cleaned.substring(0, cleaned.length() - 1).trim();
        }

        if (cleaned.isBlank() || cleaned.equals(".")) {
            return normalizedWorkspace;
        }

        Path appRoot = normalizedWorkspace.resolve(cleaned).normalize();

        // Path traversal guard
        if (!appRoot.startsWith(normalizedWorkspace)) {
            throw new BuildException("Potential path traversal in rootDirectory: " + configuredRootDir, "INVALID_ROOT_DIRECTORY");
        }

        if (!Files.exists(appRoot) || !Files.isDirectory(appRoot)) {
            throw new BuildException("Configured root directory does not exist: " + configuredRootDir, "ROOT_DIRECTORY_NOT_FOUND");
        }

        return appRoot;
    }

    private int detectDockerfilePort(Path dockerfilePath, Integer projectPort) {
        if (projectPort != null && projectPort != 8080) {
            return projectPort;
        }

        try {
            String content = Files.readString(dockerfilePath);
            Matcher matcher = DOCKERFILE_EXPOSE_PATTERN.matcher(content);
            if (matcher.find()) {
                int exposed = Integer.parseInt(matcher.group(1));
                if (exposed > 0 && exposed <= 65535) {
                    return exposed;
                }
            }
        } catch (Exception ex) {
            log.debug("Could not read EXPOSE port from Dockerfile: {}", ex.getMessage());
        }

        return projectPort != null ? projectPort : 8080;
    }

    private int detectMavenPort(Path appRoot, Integer projectPort) {
        if (projectPort != null && projectPort != 8080) {
            return projectPort;
        }

        // Try application.properties
        Path propsPath = appRoot.resolve("src/main/resources/application.properties");
        if (Files.exists(propsPath)) {
            try {
                String content = Files.readString(propsPath);
                Matcher matcher = PROPERTIES_PORT_PATTERN.matcher(content);
                if (matcher.find()) {
                    int parsed = Integer.parseInt(matcher.group(1));
                    if (parsed > 0 && parsed <= 65535) {
                        return parsed;
                    }
                }
            } catch (IOException ignored) {}
        }

        // Try application.yml
        Path ymlPath = appRoot.resolve("src/main/resources/application.yml");
        if (!Files.exists(ymlPath)) {
            ymlPath = appRoot.resolve("src/main/resources/application.yaml");
        }
        if (Files.exists(ymlPath)) {
            try {
                String content = Files.readString(ymlPath);
                Matcher matcher = YAML_PORT_PATTERN.matcher(content);
                if (matcher.find()) {
                    int parsed = Integer.parseInt(matcher.group(1));
                    if (parsed > 0 && parsed <= 65535) {
                        return parsed;
                    }
                }
            } catch (IOException ignored) {}
        }

        return projectPort != null ? projectPort : 8080;
    }

    private String generateImageTag(Project project, Deployment deployment) {
        String baseName = project.getNormalizedName() != null && !project.getNormalizedName().isBlank()
                ? project.getNormalizedName().replaceAll("[^a-zA-Z0-9_-]", "-")
                : "app";

        String tagSuffix;
        if (deployment.getCommitSha() != null && deployment.getCommitSha().length() >= 7) {
            tagSuffix = deployment.getCommitSha().substring(0, 7);
        } else {
            tagSuffix = deployment.getId().toString().substring(0, 8);
        }

        return String.format("shipit/%s:%s", baseName, tagSuffix);
    }
}

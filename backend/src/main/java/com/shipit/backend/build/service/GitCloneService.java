package com.shipit.backend.build.service;

import com.shipit.backend.build.config.BuildProperties;
import com.shipit.backend.build.exception.BuildException;
import com.shipit.backend.build.logging.BuildLogBuffer;
import com.shipit.backend.build.logging.LogSource;
import com.shipit.backend.build.process.ProcessExecutor;
import com.shipit.backend.build.process.ProcessResult;
import com.shipit.backend.build.workspace.WorkspaceManager;
import com.shipit.backend.deployment.entity.Deployment;
import com.shipit.backend.github.entity.GitHubConnection;
import com.shipit.backend.github.entity.GitHubRepository;
import com.shipit.backend.github.service.GitHubInstallationTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/**
 * Service responsible for securely cloning source code from GitHub into isolated deployment workspaces
 * using short-lived installation access tokens.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GitCloneService {

    private final GitHubInstallationTokenService tokenService;
    private final ProcessExecutor processExecutor;
    private final WorkspaceManager workspaceManager;
    private final BuildProperties buildProperties;

    /**
     * Clones the repository for the specified deployment into an isolated scratch workspace.
     * Checks out the deployment's target branch and commit SHA.
     * All installation tokens are strictly masked in logs and exceptions.
     */
    public Path cloneRepository(Deployment deployment, BuildLogBuffer logBuffer) {
        if (deployment == null) {
            throw new IllegalArgumentException("Deployment must not be null");
        }
        if (deployment.getProject() == null || deployment.getProject().getRepository() == null) {
            throw new BuildException("Deployment project does not have a linked GitHub repository", "REPOSITORY_NOT_FOUND");
        }

        GitHubRepository repo = deployment.getProject().getRepository();
        GitHubConnection connection = repo.getConnection();
        if (connection == null || connection.getInstallationId() == null) {
            throw new BuildException("GitHub connection or installation ID is missing for repository: " + repo.getFullName(), "INVALID_GITHUB_CONNECTION");
        }

        String branch = deployment.getCommitBranch() != null && !deployment.getCommitBranch().isBlank()
                ? deployment.getCommitBranch()
                : (repo.getDefaultBranch() != null ? repo.getDefaultBranch() : "main");

        String commitSha = deployment.getCommitSha();

        log.info("Initiating clone for deployment {} (repo: {}, branch: {}, commit: {})",
                deployment.getId(), repo.getFullName(), branch, commitSha);

        if (logBuffer != null) {
            logBuffer.append(LogSource.SYSTEM, String.format("Cloning repository %s on branch %s...", repo.getFullName(), branch));
        }

        // 1. Obtain ephemeral token
        String token = tokenService.getInstallationToken(connection.getInstallationId());
        if (token == null || token.isBlank()) {
            throw new BuildException("Failed to acquire GitHub installation token for repository: " + repo.getFullName(), "TOKEN_GENERATION_FAILED");
        }

        // 2. Build authenticated clone URL
        String authenticatedUrl = buildAuthenticatedCloneUrl(repo.getCloneUrl(), token);

        // 3. Create isolated workspace
        Path workspace = workspaceManager.createWorkspace(deployment.getId());

        // 4. Execute git clone
        Set<String> sensitiveValues = Set.of(token);
        long timeout = buildProperties.getTimeoutSeconds();

        List<String> cloneCommand = List.of(
                "git", "clone",
                "--depth", "50",
                "--branch", branch,
                authenticatedUrl,
                "."
        );

        ProcessResult cloneResult = processExecutor.execute(cloneCommand, workspace, logBuffer, timeout, sensitiveValues);
        if (!cloneResult.isSuccess()) {
            String error = cloneResult.isTimedOut()
                    ? "Git clone timed out after " + timeout + " seconds"
                    : "Git clone failed with exit code " + cloneResult.getExitCode();
            log.error("Deployment {} clone failed: {}", deployment.getId(), error);
            throw new BuildException(error, "GIT_CLONE_FAILED");
        }

        // 5. Checkout specific commitSha if specified
        if (commitSha != null && !commitSha.isBlank()) {
            if (logBuffer != null) {
                logBuffer.append(LogSource.SYSTEM, "Checking out commit " + commitSha + "...");
            }
            List<String> checkoutCommand = List.of("git", "checkout", commitSha);
            ProcessResult checkoutResult = processExecutor.execute(checkoutCommand, workspace, logBuffer, timeout, sensitiveValues);
            if (!checkoutResult.isSuccess()) {
                String error = "Failed to checkout commit " + commitSha + " (exit code " + checkoutResult.getExitCode() + ")";
                log.error("Deployment {} checkout failed: {}", deployment.getId(), error);
                throw new BuildException(error, "GIT_CHECKOUT_FAILED");
            }
        }

        if (logBuffer != null) {
            logBuffer.append(LogSource.SYSTEM, "Repository successfully cloned and ready for build.");
        }
        log.info("Successfully cloned deployment {} workspace at: {}", deployment.getId(), workspace);
        return workspace;
    }

    /**
     * Injects the GitHub App installation token into the clone URL:
     * https://x-access-token:&lt;token&gt;@github.com/&lt;owner&gt;/&lt;repo&gt;.git
     */
    public String buildAuthenticatedCloneUrl(String cloneUrl, String token) {
        if (cloneUrl == null || cloneUrl.isBlank()) {
            throw new IllegalArgumentException("Clone URL must not be blank");
        }
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Token must not be blank");
        }

        try {
            URI uri = URI.create(cloneUrl);
            String host = uri.getHost();
            String path = uri.getPath();
            return String.format("https://x-access-token:%s@%s%s", token, host, path);
        } catch (Exception ex) {
            return cloneUrl.replace("https://", "https://x-access-token:" + token + "@");
        }
    }
}

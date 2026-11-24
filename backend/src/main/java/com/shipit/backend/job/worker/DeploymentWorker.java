package com.shipit.backend.job.worker;

import com.shipit.backend.build.exception.BuildException;
import com.shipit.backend.container.exception.ContainerException;
import com.shipit.backend.job.config.DeploymentWorkerProperties;
import com.shipit.backend.job.entity.DeploymentJob;
import com.shipit.backend.job.entity.JobStatus;
import com.shipit.backend.job.service.DeploymentJobService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
@RequiredArgsConstructor
public class DeploymentWorker {

    private final DeploymentJobService jobService;
    private final DeploymentTaskHandler taskHandler;
    private final DeploymentWorkerProperties properties;

    @Getter
    private String workerId;
    private final AtomicBoolean running = new AtomicBoolean(false);

    @PostConstruct
    public void init() {
        if (properties.getWorkerId() != null && !properties.getWorkerId().isBlank()) {
            this.workerId = properties.getWorkerId().trim();
        } else {
            this.workerId = "worker-" + UUID.randomUUID().toString().substring(0, 8);
        }
        running.set(true);
        log.info("Initialized DeploymentWorker [{}] (enabled: {})", workerId, properties.isEnabled());
    }

    @PreDestroy
    public void shutdown() {
        log.info("Shutting down DeploymentWorker [{}]", workerId);
        running.set(false);
    }

    /**
     * Polls the database queue for pending deployment jobs, claims the next job
     * atomically via FOR UPDATE SKIP LOCKED, and executes it.
     */
    @Scheduled(fixedDelayString = "${shipit.worker.poll-interval-ms:2000}")
    public void pollAndExecute() {
        if (!properties.isEnabled() || !running.get()) {
            return;
        }

        try {
            Optional<DeploymentJob> claimedJobOpt = jobService.claimNextJob(workerId);
            if (claimedJobOpt.isEmpty()) {
                return;
            }

            DeploymentJob job = claimedJobOpt.get();
            log.info("Worker [{}] processing claimed job id: {}", workerId, job.getId());
            executeJob(job);
        } catch (Exception e) {
            log.error("Worker [{}] encountered unexpected error during polling loop: {}", workerId, e.getMessage(), e);
        }
    }

    /**
     * Periodically scans for stale jobs belonging to dead or partitioned workers.
     */
    @Scheduled(fixedDelayString = "${shipit.worker.recovery-interval-ms:60000}")
    public void recoverStaleJobs() {
        if (!properties.isEnabled() || !running.get()) {
            return;
        }

        try {
            Duration threshold = Duration.ofSeconds(properties.getStaleThresholdSeconds());
            int recovered = jobService.recoverStaleJobs(threshold);
            if (recovered > 0) {
                log.info("Worker [{}] recovered {} stale background jobs", workerId, recovered);
            }
        } catch (Exception e) {
            log.error("Worker [{}] error during stale job recovery scan: {}", workerId, e.getMessage(), e);
        }
    }

    private void executeJob(DeploymentJob job) {
        try {
            taskHandler.handleDeployment(job);

            DeploymentJob currentJob = jobService.getJobEntity(job.getId());
            if (currentJob.getStatus() == JobStatus.RUNNING) {
                jobService.completeJob(job.getId());
                log.info("Worker [{}] successfully finished job id: {}", workerId, job.getId());
            } else {
                log.info("Worker [{}] job id: {} is no longer RUNNING (current: {}); skipping completion",
                        workerId, job.getId(), currentJob.getStatus());
            }
        } catch (Exception e) {
            log.error("Worker [{}] job id: {} failed during execution: {}", workerId, job.getId(), e.getMessage());
            try {
                DeploymentJob currentJob = jobService.getJobEntity(job.getId());
                if (currentJob.getStatus() == JobStatus.RUNNING) {
                    boolean canRetry = isRetryable(e);
                    jobService.failJob(job.getId(), e.getMessage(), canRetry);
                }
            } catch (Exception ex) {
                log.error("Worker [{}] failed to update failure status for job {}: {}", workerId, job.getId(), ex.getMessage());
            }
        }
    }

    /**
     * Determines whether an execution exception represents a transient failure eligible for retry.
     * Known deterministic application, configuration, or compilation errors are explicitly non-retryable.
     * Unknown exceptions are not blindly retried.
     */
    public boolean isRetryable(Exception e) {
        if (e == null) {
            return false;
        }

        // 1. Explicit deterministic non-retryable application errors
        if (e instanceof BuildException be) {
            String code = be.getErrorCode();
            if ("UNSUPPORTED_PROJECT_TYPE".equals(code)
                    || "INVALID_ROOT_DIRECTORY".equals(code)
                    || "ROOT_DIRECTORY_NOT_FOUND".equals(code)
                    || "DOCKERFILE_NOT_FOUND".equals(code)
                    || "DOCKERFILE_GENERATION_FAILED".equals(code)
                    || "REPOSITORY_NOT_FOUND".equals(code)
                    || "INVALID_GITHUB_CONNECTION".equals(code)
                    || "GIT_CHECKOUT_FAILED".equals(code)) {
                return false;
            }

            // Docker build failure refinement:
            // "DOCKER_BUILD_FAILED" represents a non-zero exit code from "docker build".
            // If the failure is due to daemon/infrastructure issues (daemon down, disk full, etc.), it is retryable.
            // If the failure is an application compilation or build script failure, it is deterministic (non-retryable).
            if ("DOCKER_BUILD_FAILED".equals(code)) {
                return isTransientDockerFailure(e);
            }

            // Explicit transient build codes
            if ("TOKEN_GENERATION_FAILED".equals(code) || "BUILD_TIMEOUT".equals(code)) {
                return true;
            }

            if ("GIT_CLONE_FAILED".equals(code)) {
                return isTransientNetworkOrCloneFailure(e);
            }
        }

        if (e instanceof ContainerException ce) {
            String code = ce.getErrorCode();
            if ("PORT_ALLOCATION_FAILED".equals(code)
                    || "CONTAINER_START_TIMEOUT".equals(code)
                    || "HEALTH_CHECK_TIMEOUT".equals(code)) {
                return true;
            }
            if (isTransientDockerFailure(e)) {
                return true;
            }
            return false;
        }

        // 2. Explicit transient infrastructure / network / docker daemon exceptions
        if (isNetworkOrTimeoutException(e) || isTransientDockerFailure(e)) {
            return true;
        }

        // 3. Fallback: Unknown or unclassified exceptions are NOT blindly retried
        return false;
    }

    private boolean isTransientDockerFailure(Throwable t) {
        String message = collectErrorMessages(t).toLowerCase(Locale.ROOT);
        return message.contains("cannot connect to the docker daemon")
                || message.contains("daemon not running")
                || message.contains("daemon is not running")
                || message.contains("is the docker daemon running")
                || message.contains("connection refused")
                || message.contains("i/o timeout")
                || message.contains("no space left on device")
                || message.contains("server error: 502")
                || message.contains("server error: 503")
                || message.contains("error response from daemon: dial unix")
                || message.contains("pipe: broken pipe")
                || isNetworkOrTimeoutException(t);
    }

    private boolean isTransientNetworkOrCloneFailure(Throwable t) {
        if (isNetworkOrTimeoutException(t)) {
            return true;
        }
        String message = collectErrorMessages(t).toLowerCase(Locale.ROOT);
        return message.contains("could not resolve host")
                || message.contains("connection timed out")
                || message.contains("connection reset")
                || message.contains("network is unreachable")
                || message.contains("ssl handshake")
                || message.contains("early eof")
                || message.contains("remote end hung up unexpectedly");
    }

    private boolean isNetworkOrTimeoutException(Throwable t) {
        Throwable current = t;
        while (current != null) {
            if (current instanceof java.net.SocketException
                    || current instanceof java.net.SocketTimeoutException
                    || current instanceof java.net.ConnectException
                    || current instanceof java.net.UnknownHostException
                    || current instanceof java.io.InterruptedIOException
                    || current instanceof java.util.concurrent.TimeoutException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private String collectErrorMessages(Throwable t) {
        StringBuilder sb = new StringBuilder();
        Throwable current = t;
        while (current != null) {
            if (current.getMessage() != null) {
                sb.append(current.getMessage()).append(" ");
            }
            current = current.getCause();
        }
        return sb.toString();
    }

    public boolean isRunning() {
        return running.get();
    }
}

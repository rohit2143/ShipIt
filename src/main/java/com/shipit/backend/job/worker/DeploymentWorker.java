package com.shipit.backend.job.worker;

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
                    jobService.failJob(job.getId(), e.getMessage(), true);
                }
            } catch (Exception ex) {
                log.error("Worker [{}] failed to update failure status for job {}: {}", workerId, job.getId(), ex.getMessage());
            }
        }
    }

    public boolean isRunning() {
        return running.get();
    }
}

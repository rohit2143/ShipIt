package com.shipit.backend.job.service;

import com.shipit.backend.common.exception.BadRequestException;
import com.shipit.backend.common.exception.ConflictException;
import com.shipit.backend.common.exception.ResourceNotFoundException;
import com.shipit.backend.deployment.entity.Deployment;
import com.shipit.backend.job.dto.JobQueueStatsResponse;
import com.shipit.backend.job.dto.JobResponse;
import com.shipit.backend.job.entity.DeploymentJob;
import com.shipit.backend.job.entity.JobStatus;
import com.shipit.backend.job.entity.JobType;
import com.shipit.backend.job.repository.DeploymentJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeploymentJobService {

    private static final Set<JobStatus> ACTIVE_STATUSES = Set.of(JobStatus.PENDING, JobStatus.RUNNING);

    private final DeploymentJobRepository jobRepository;

    /**
     * Enqueues a new background deployment job for the given deployment.
     * Enforces duplicate execution protection: prevents creating a job if one is already active (PENDING or RUNNING).
     */
    @Transactional
    public DeploymentJob createJob(Deployment deployment, int priority) {
        if (deployment == null) {
            throw new BadRequestException("Deployment must not be null", "INVALID_DEPLOYMENT");
        }

        UUID deploymentId = deployment.getId();
        if (deploymentId != null && jobRepository.existsByDeploymentIdAndStatusIn(deploymentId, ACTIVE_STATUSES)) {
            log.warn("Rejected duplicate job creation for deployment {}", deploymentId);
            throw new ConflictException(
                    "An active background job is already pending or running for deployment: " + deploymentId,
                    "ACTIVE_JOB_EXISTS"
            );
        }

        DeploymentJob job = DeploymentJob.builder()
                .deployment(deployment)
                .type(JobType.DEPLOYMENT)
                .status(JobStatus.PENDING)
                .priority(priority)
                .queuedAt(Instant.now())
                .build();

        DeploymentJob saved = jobRepository.save(job);
        log.info("Created background deployment job {} for deployment {} with priority {}",
                saved.getId(), deploymentId, priority);
        return saved;
    }

    /**
     * Atomically claims the next highest priority PENDING job using FOR UPDATE SKIP LOCKED.
     * Guarantees race-free worker assignment across concurrent threads.
     */
    @Transactional
    public Optional<DeploymentJob> claimNextJob(String workerId) {
        if (workerId == null || workerId.isBlank()) {
            throw new BadRequestException("Worker ID must not be blank", "INVALID_WORKER_ID");
        }

        Optional<DeploymentJob> pendingJobOpt = jobRepository.findNextPendingJobForUpdate();
        if (pendingJobOpt.isEmpty()) {
            return Optional.empty();
        }

        DeploymentJob job = pendingJobOpt.get();
        job.claim(workerId);
        DeploymentJob saved = jobRepository.save(job);
        log.info("Worker [{}] claimed deployment job {} for deployment {}",
                workerId, saved.getId(), saved.getDeployment().getId());
        return Optional.of(saved);
    }

    /**
     * Periodically updates the heartbeat timestamp for an actively running job to signal worker liveliness.
     */
    @Transactional
    public void recordHeartbeat(UUID jobId) {
        DeploymentJob job = getJobEntity(jobId);
        job.updateHeartbeat();
        jobRepository.save(job);
        log.debug("Updated heartbeat for running job {}", jobId);
    }

    /**
     * Marks a job as successfully completed.
     */
    @Transactional
    public DeploymentJob completeJob(UUID jobId) {
        DeploymentJob job = getJobEntity(jobId);
        job.complete();
        DeploymentJob saved = jobRepository.save(job);
        log.info("Job {} completed successfully", jobId);
        return saved;
    }

    /**
     * Handles job failure with retry evaluation.
     * If retries are enabled and remaining, the job is re-queued as PENDING;
     * otherwise, it is permanently marked as FAILED.
     */
    @Transactional
    public DeploymentJob failJob(UUID jobId, String errorMessage, boolean canRetry) {
        DeploymentJob job = getJobEntity(jobId);

        if (canRetry && job.canRetry()) {
            job.scheduleRetry(errorMessage);
            DeploymentJob saved = jobRepository.save(job);
            log.warn("Job {} failed with error [{}]. Scheduled retry {}/{}",
                    jobId, errorMessage, saved.getRetryCount(), saved.getMaxRetries());
            return saved;
        }

        job.fail(errorMessage);
        DeploymentJob saved = jobRepository.save(job);
        log.error("Job {} permanently failed after {} retries: {}",
                jobId, saved.getRetryCount(), errorMessage);
        return saved;
    }

    /**
     * Cancels a pending or running job.
     */
    @Transactional
    public DeploymentJob cancelJob(UUID jobId) {
        DeploymentJob job = getJobEntity(jobId);

        if (job.getStatus().isTerminal()) {
            if (job.getStatus() == JobStatus.CANCELLED) {
                return job;
            }
            throw new ConflictException(
                    "Cannot cancel job in terminal status: " + job.getStatus(),
                    "JOB_ALREADY_TERMINAL"
            );
        }

        job.cancel();
        DeploymentJob saved = jobRepository.save(job);
        log.info("Job {} cancelled", jobId);
        return saved;
    }

    /**
     * Scans for RUNNING jobs whose worker heartbeat has timed out past the threshold,
     * recovering them by re-queueing (if retries remain) or failing them.
     *
     * @param staleThreshold Threshold duration after which a missing heartbeat indicates worker death.
     * @return Number of recovered jobs.
     */
    @Transactional
    public int recoverStaleJobs(Duration staleThreshold) {
        if (staleThreshold == null || staleThreshold.isNegative() || staleThreshold.isZero()) {
            throw new BadRequestException("Stale threshold must be a positive duration", "INVALID_THRESHOLD");
        }

        Instant thresholdTime = Instant.now().minus(staleThreshold);
        List<DeploymentJob> staleJobs = new ArrayList<>(
                jobRepository.findByStatusAndHeartbeatAtBefore(JobStatus.RUNNING, thresholdTime)
        );
        staleJobs.addAll(
                jobRepository.findByStatusAndHeartbeatAtIsNullAndClaimedAtBefore(JobStatus.RUNNING, thresholdTime)
        );

        if (staleJobs.isEmpty()) {
            return 0;
        }

        int recoveredCount = 0;
        for (DeploymentJob job : staleJobs) {
            String deadWorker = job.getClaimedBy() != null ? job.getClaimedBy() : "unknown-worker";
            if (job.canRetry()) {
                job.scheduleRetry("Recovered from stale/crashed worker: " + deadWorker);
                jobRepository.save(job);
                log.warn("Recovered stale job {} (previously claimed by [{}]). Re-queued for retry {}/{}",
                        job.getId(), deadWorker, job.getRetryCount(), job.getMaxRetries());
            } else {
                job.fail("Worker [" + deadWorker + "] timed out and job exhausted max retries (" + job.getMaxRetries() + ")");
                jobRepository.save(job);
                log.error("Stale job {} permanently failed: exceeded max retries", job.getId());
            }
            recoveredCount++;
        }

        return recoveredCount;
    }

    /**
     * Retrieves a job entity by ID.
     */
    @Transactional(readOnly = true)
    public DeploymentJob getJobEntity(UUID id) {
        return jobRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("DeploymentJob", "id", id));
    }

    /**
     * Retrieves a job response by ID.
     */
    @Transactional(readOnly = true)
    public JobResponse getJobResponse(UUID id) {
        return JobResponse.fromEntity(getJobEntity(id));
    }

    /**
     * Retrieves the latest job for a given deployment.
     */
    @Transactional(readOnly = true)
    public Optional<DeploymentJob> getLatestJobForDeployment(UUID deploymentId) {
        return jobRepository.findTopByDeploymentIdOrderByCreatedAtDesc(deploymentId);
    }

    /**
     * Checks if an active job currently exists for a deployment.
     */
    @Transactional(readOnly = true)
    public boolean hasActiveJob(UUID deploymentId) {
        return jobRepository.existsByDeploymentIdAndStatusIn(deploymentId, ACTIVE_STATUSES);
    }

    /**
     * Retrieves a job response by ID, verifying that the job belongs to the authenticated user's project.
     */
    @Transactional(readOnly = true)
    public JobResponse getJobForUser(UUID userId, UUID jobId) {
        if (userId == null || jobId == null) {
            throw new BadRequestException("User ID and Job ID must not be null", "INVALID_ARGUMENTS");
        }
        DeploymentJob job = jobRepository.findByIdAndUserId(jobId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("DeploymentJob", "id", jobId));
        return JobResponse.fromEntity(job);
    }

    /**
     * Retrieves the latest job for a deployment, verifying that the deployment belongs to the authenticated user.
     */
    @Transactional(readOnly = true)
    public JobResponse getLatestJobForDeploymentAndUser(UUID userId, UUID deploymentId) {
        if (userId == null || deploymentId == null) {
            throw new BadRequestException("User ID and Deployment ID must not be null", "INVALID_ARGUMENTS");
        }
        List<DeploymentJob> jobs = jobRepository.findAllByDeploymentIdAndUserIdOrderByCreatedAtDesc(deploymentId, userId);
        if (jobs.isEmpty()) {
            throw new ResourceNotFoundException("No background job found for deployment: " + deploymentId);
        }
        return JobResponse.fromEntity(jobs.get(0));
    }

    /**
     * Retrieves all jobs for a deployment belonging to the user, ordered newest first.
     */
    @Transactional(readOnly = true)
    public List<JobResponse> getAllJobsForDeploymentAndUser(UUID userId, UUID deploymentId) {
        if (userId == null || deploymentId == null) {
            throw new BadRequestException("User ID and Deployment ID must not be null", "INVALID_ARGUMENTS");
        }
        List<DeploymentJob> jobs = jobRepository.findAllByDeploymentIdAndUserIdOrderByCreatedAtDesc(deploymentId, userId);
        return jobs.stream().map(JobResponse::fromEntity).toList();
    }

    /**
     * Retrieves current queue statistics across all job statuses.
     */
    @Transactional(readOnly = true)
    public JobQueueStatsResponse getQueueStats() {
        long pending = jobRepository.countByStatus(JobStatus.PENDING);
        long running = jobRepository.countByStatus(JobStatus.RUNNING);
        long completed = jobRepository.countByStatus(JobStatus.COMPLETED);
        long failed = jobRepository.countByStatus(JobStatus.FAILED);
        long cancelled = jobRepository.countByStatus(JobStatus.CANCELLED);
        long total = pending + running + completed + failed + cancelled;

        return JobQueueStatsResponse.builder()
                .pendingCount(pending)
                .runningCount(running)
                .completedCount(completed)
                .failedCount(failed)
                .cancelledCount(cancelled)
                .totalCount(total)
                .build();
    }
}

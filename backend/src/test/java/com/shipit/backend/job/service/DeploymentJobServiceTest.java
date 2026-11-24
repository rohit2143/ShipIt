package com.shipit.backend.job.service;

import com.shipit.backend.common.exception.BadRequestException;
import com.shipit.backend.common.exception.ConflictException;
import com.shipit.backend.common.exception.ResourceNotFoundException;
import com.shipit.backend.deployment.entity.Deployment;
import com.shipit.backend.deployment.entity.DeploymentStatus;
import com.shipit.backend.deployment.repository.DeploymentRepository;
import com.shipit.backend.job.dto.JobQueueStatsResponse;
import com.shipit.backend.job.dto.JobResponse;
import com.shipit.backend.job.entity.DeploymentJob;
import com.shipit.backend.job.entity.JobStatus;
import com.shipit.backend.job.entity.JobType;
import com.shipit.backend.job.repository.DeploymentJobRepository;
import com.shipit.backend.log.service.DeploymentLogService;
import com.shipit.backend.project.entity.Project;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeploymentJobServiceTest {

    @Mock
    private DeploymentJobRepository jobRepository;

    @Mock
    private DeploymentRepository deploymentRepository;

    @Mock
    private DeploymentLogService deploymentLogService;

    @InjectMocks
    private DeploymentJobService jobService;

    private Deployment sampleDeployment;
    private UUID deploymentId;
    private UUID jobId;

    @BeforeEach
    void setUp() {
        deploymentId = UUID.randomUUID();
        jobId = UUID.randomUUID();

        Project project = Project.builder()
                .id(UUID.randomUUID())
                .name("Test Project")
                .build();

        sampleDeployment = Deployment.builder()
                .id(deploymentId)
                .project(project)
                .build();
    }

    @Test
    @DisplayName("createJob creates and persists a PENDING DeploymentJob")
    void testCreateJobSuccess() {
        when(jobRepository.existsByDeploymentIdAndStatusIn(eq(deploymentId), any())).thenReturn(false);
        when(jobRepository.save(any(DeploymentJob.class))).thenAnswer(invocation -> {
            DeploymentJob job = invocation.getArgument(0);
            job.setId(jobId);
            return job;
        });

        DeploymentJob result = jobService.createJob(sampleDeployment, 5);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(jobId);
        assertThat(result.getStatus()).isEqualTo(JobStatus.PENDING);
        assertThat(result.getPriority()).isEqualTo(5);
        assertThat(result.getType()).isEqualTo(JobType.DEPLOYMENT);

        verify(jobRepository).existsByDeploymentIdAndStatusIn(eq(deploymentId), any());
        verify(jobRepository).save(any(DeploymentJob.class));
    }

    @Test
    @DisplayName("createJob throws BadRequestException when deployment is null")
    void testCreateJobNullDeployment() {
        assertThatThrownBy(() -> jobService.createJob(null, 0))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Deployment must not be null");

        verifyNoInteractions(jobRepository);
    }

    @Test
    @DisplayName("createJob throws ConflictException when active job already exists for deployment")
    void testCreateJobDuplicateProtection() {
        when(jobRepository.existsByDeploymentIdAndStatusIn(eq(deploymentId), any())).thenReturn(true);

        assertThatThrownBy(() -> jobService.createJob(sampleDeployment, 0))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("An active background job is already pending or running");

        verify(jobRepository).existsByDeploymentIdAndStatusIn(eq(deploymentId), any());
        verify(jobRepository, never()).save(any());
    }

    @Test
    @DisplayName("claimNextJob claims the next pending job atomically")
    void testClaimNextJobSuccess() {
        DeploymentJob pendingJob = DeploymentJob.builder()
                .id(jobId)
                .deployment(sampleDeployment)
                .status(JobStatus.PENDING)
                .queuedAt(Instant.now().minusSeconds(30))
                .build();

        when(jobRepository.findNextPendingJobForUpdate()).thenReturn(Optional.of(pendingJob));
        when(jobRepository.save(any(DeploymentJob.class))).thenAnswer(inv -> inv.getArgument(0));

        Optional<DeploymentJob> result = jobService.claimNextJob("worker-1");

        assertThat(result).isPresent();
        assertThat(result.get().getStatus()).isEqualTo(JobStatus.RUNNING);
        assertThat(result.get().getClaimedBy()).isEqualTo("worker-1");
        assertThat(result.get().getClaimedAt()).isNotNull();
        assertThat(result.get().getHeartbeatAt()).isNotNull();

        verify(jobRepository).findNextPendingJobForUpdate();
        verify(jobRepository).save(pendingJob);
    }

    @Test
    @DisplayName("claimNextJob returns empty when no pending jobs exist")
    void testClaimNextJobNoPending() {
        when(jobRepository.findNextPendingJobForUpdate()).thenReturn(Optional.empty());

        Optional<DeploymentJob> result = jobService.claimNextJob("worker-1");

        assertThat(result).isEmpty();
        verify(jobRepository).findNextPendingJobForUpdate();
        verify(jobRepository, never()).save(any());
    }

    @Test
    @DisplayName("claimNextJob throws BadRequestException when workerId is blank")
    void testClaimNextJobBlankWorker() {
        assertThatThrownBy(() -> jobService.claimNextJob("  "))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Worker ID must not be blank");

        verifyNoInteractions(jobRepository);
    }

    @Test
    @DisplayName("recordHeartbeat updates heartbeat timestamp for running job")
    void testRecordHeartbeatSuccess() {
        DeploymentJob runningJob = DeploymentJob.builder()
                .id(jobId)
                .status(JobStatus.RUNNING)
                .heartbeatAt(Instant.now().minusSeconds(60))
                .build();

        when(jobRepository.findById(jobId)).thenReturn(Optional.of(runningJob));
        when(jobRepository.save(any(DeploymentJob.class))).thenAnswer(inv -> inv.getArgument(0));

        jobService.recordHeartbeat(jobId);

        assertThat(runningJob.getHeartbeatAt()).isAfter(Instant.now().minusSeconds(10));
        verify(jobRepository).save(runningJob);
    }

    @Test
    @DisplayName("recordHeartbeat throws ResourceNotFoundException when job does not exist")
    void testRecordHeartbeatNotFound() {
        when(jobRepository.findById(jobId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> jobService.recordHeartbeat(jobId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("DeploymentJob not found");
    }

    @Test
    @DisplayName("completeJob marks job as COMPLETED and sets finishedAt")
    void testCompleteJobSuccess() {
        DeploymentJob runningJob = DeploymentJob.builder()
                .id(jobId)
                .status(JobStatus.RUNNING)
                .build();

        when(jobRepository.findById(jobId)).thenReturn(Optional.of(runningJob));
        when(jobRepository.save(any(DeploymentJob.class))).thenAnswer(inv -> inv.getArgument(0));

        DeploymentJob result = jobService.completeJob(jobId);

        assertThat(result.getStatus()).isEqualTo(JobStatus.COMPLETED);
        assertThat(result.getFinishedAt()).isNotNull();
        verify(jobRepository).save(runningJob);
    }

    @Test
    @DisplayName("failJob schedules retry when canRetry is true and retries remain")
    void testFailJobSchedulesRetry() {
        DeploymentJob runningJob = DeploymentJob.builder()
                .id(jobId)
                .status(JobStatus.RUNNING)
                .retryCount(0)
                .maxRetries(3)
                .claimedBy("worker-1")
                .build();

        when(jobRepository.findById(jobId)).thenReturn(Optional.of(runningJob));
        when(jobRepository.save(any(DeploymentJob.class))).thenAnswer(inv -> inv.getArgument(0));

        DeploymentJob result = jobService.failJob(jobId, "Docker daemon timeout", true);

        assertThat(result.getStatus()).isEqualTo(JobStatus.PENDING);
        assertThat(result.getRetryCount()).isEqualTo(1);
        assertThat(result.getClaimedBy()).isNull();
        assertThat(result.getErrorMessage()).isEqualTo("Docker daemon timeout");
        verify(jobRepository).save(runningJob);
    }

    @Test
    @DisplayName("failJob resets associated FAILED deployment back to QUEUED and logs audit entry")
    void testFailJobSchedulesRetry_ResetsDeploymentToQueuedAndLogs() {
        sampleDeployment.setStatus(DeploymentStatus.FAILED);
        sampleDeployment.setFinishedAt(Instant.now());

        DeploymentJob runningJob = DeploymentJob.builder()
                .id(jobId)
                .deployment(sampleDeployment)
                .status(JobStatus.RUNNING)
                .retryCount(0)
                .maxRetries(3)
                .claimedBy("worker-1")
                .build();

        when(jobRepository.findById(jobId)).thenReturn(Optional.of(runningJob));
        when(jobRepository.save(any(DeploymentJob.class))).thenAnswer(inv -> inv.getArgument(0));
        when(deploymentRepository.findById(deploymentId)).thenReturn(Optional.of(sampleDeployment));
        when(deploymentRepository.save(any(Deployment.class))).thenAnswer(inv -> inv.getArgument(0));

        DeploymentJob result = jobService.failJob(jobId, "Transient network timeout", true);

        assertThat(result.getStatus()).isEqualTo(JobStatus.PENDING);
        assertThat(result.getRetryCount()).isEqualTo(1);
        assertThat(sampleDeployment.getStatus()).isEqualTo(DeploymentStatus.QUEUED);
        assertThat(sampleDeployment.getFinishedAt()).isNull();

        verify(deploymentRepository).save(sampleDeployment);
        verify(deploymentLogService).appendLog(eq(deploymentId), any(), any(), contains("retry attempt 1/3"), any());
        verify(deploymentLogService).recordStatusChange(eq(deploymentId), eq(DeploymentStatus.QUEUED), contains("retry attempt 1/3"));
    }

    @Test
    @DisplayName("failJob permanently fails when retries are exhausted")
    void testFailJobPermanentlyFailsWhenRetriesExhausted() {
        DeploymentJob runningJob = DeploymentJob.builder()
                .id(jobId)
                .status(JobStatus.RUNNING)
                .retryCount(3)
                .maxRetries(3)
                .claimedBy("worker-1")
                .build();

        when(jobRepository.findById(jobId)).thenReturn(Optional.of(runningJob));
        when(jobRepository.save(any(DeploymentJob.class))).thenAnswer(inv -> inv.getArgument(0));

        DeploymentJob result = jobService.failJob(jobId, "Out of memory", true);

        assertThat(result.getStatus()).isEqualTo(JobStatus.FAILED);
        assertThat(result.getRetryCount()).isEqualTo(3);
        assertThat(result.getFinishedAt()).isNotNull();
        assertThat(result.getErrorMessage()).isEqualTo("Out of memory");
        verify(jobRepository).save(runningJob);
    }

    @Test
    @DisplayName("failJob permanently fails when canRetry is false regardless of count")
    void testFailJobDirectFailure() {
        DeploymentJob runningJob = DeploymentJob.builder()
                .id(jobId)
                .status(JobStatus.RUNNING)
                .retryCount(0)
                .maxRetries(3)
                .build();

        when(jobRepository.findById(jobId)).thenReturn(Optional.of(runningJob));
        when(jobRepository.save(any(DeploymentJob.class))).thenAnswer(inv -> inv.getArgument(0));

        DeploymentJob result = jobService.failJob(jobId, "Invalid syntax", false);

        assertThat(result.getStatus()).isEqualTo(JobStatus.FAILED);
        assertThat(result.getRetryCount()).isEqualTo(0);
        assertThat(result.getFinishedAt()).isNotNull();
        verify(jobRepository).save(runningJob);
    }

    @Test
    @DisplayName("cancelJob cancels pending job")
    void testCancelPendingJob() {
        DeploymentJob pendingJob = DeploymentJob.builder()
                .id(jobId)
                .status(JobStatus.PENDING)
                .build();

        when(jobRepository.findById(jobId)).thenReturn(Optional.of(pendingJob));
        when(jobRepository.save(any(DeploymentJob.class))).thenAnswer(inv -> inv.getArgument(0));

        DeploymentJob result = jobService.cancelJob(jobId);

        assertThat(result.getStatus()).isEqualTo(JobStatus.CANCELLED);
        assertThat(result.getFinishedAt()).isNotNull();
        verify(jobRepository).save(pendingJob);
    }

    @Test
    @DisplayName("cancelJob is idempotent when job is already CANCELLED")
    void testCancelAlreadyCancelledJob() {
        DeploymentJob cancelledJob = DeploymentJob.builder()
                .id(jobId)
                .status(JobStatus.CANCELLED)
                .build();

        when(jobRepository.findById(jobId)).thenReturn(Optional.of(cancelledJob));

        DeploymentJob result = jobService.cancelJob(jobId);

        assertThat(result.getStatus()).isEqualTo(JobStatus.CANCELLED);
        verify(jobRepository, never()).save(any());
    }

    @Test
    @DisplayName("cancelJob throws ConflictException when job is already COMPLETED or FAILED")
    void testCancelTerminalJobThrowsConflict() {
        DeploymentJob completedJob = DeploymentJob.builder()
                .id(jobId)
                .status(JobStatus.COMPLETED)
                .build();

        when(jobRepository.findById(jobId)).thenReturn(Optional.of(completedJob));

        assertThatThrownBy(() -> jobService.cancelJob(jobId))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Cannot cancel job in terminal status: COMPLETED");

        verify(jobRepository, never()).save(any());
    }

    @Test
    @DisplayName("recoverStaleJobs re-queues stale jobs when retries remain")
    void testRecoverStaleJobsWithRetries() {
        DeploymentJob staleJob = DeploymentJob.builder()
                .id(jobId)
                .status(JobStatus.RUNNING)
                .claimedBy("dead-worker-1")
                .retryCount(0)
                .maxRetries(3)
                .heartbeatAt(Instant.now().minusSeconds(400))
                .build();

        when(jobRepository.findByStatusAndHeartbeatAtBefore(eq(JobStatus.RUNNING), any()))
                .thenReturn(List.of(staleJob));
        when(jobRepository.findByStatusAndHeartbeatAtIsNullAndClaimedAtBefore(eq(JobStatus.RUNNING), any()))
                .thenReturn(Collections.emptyList());

        int recovered = jobService.recoverStaleJobs(Duration.ofMinutes(5));

        assertThat(recovered).isEqualTo(1);
        assertThat(staleJob.getStatus()).isEqualTo(JobStatus.PENDING);
        assertThat(staleJob.getRetryCount()).isEqualTo(1);
        assertThat(staleJob.getClaimedBy()).isNull();
        assertThat(staleJob.getErrorMessage()).contains("Recovered from stale/crashed worker: dead-worker-1");
        verify(jobRepository).save(staleJob);
    }

    @Test
    @DisplayName("recoverStaleJobs permanently fails stale jobs when retries exhausted")
    void testRecoverStaleJobsExhaustedRetries() {
        DeploymentJob staleJob = DeploymentJob.builder()
                .id(jobId)
                .status(JobStatus.RUNNING)
                .claimedBy("dead-worker-2")
                .retryCount(3)
                .maxRetries(3)
                .heartbeatAt(Instant.now().minusSeconds(400))
                .build();

        when(jobRepository.findByStatusAndHeartbeatAtBefore(eq(JobStatus.RUNNING), any()))
                .thenReturn(List.of(staleJob));
        when(jobRepository.findByStatusAndHeartbeatAtIsNullAndClaimedAtBefore(eq(JobStatus.RUNNING), any()))
                .thenReturn(Collections.emptyList());

        int recovered = jobService.recoverStaleJobs(Duration.ofMinutes(5));

        assertThat(recovered).isEqualTo(1);
        assertThat(staleJob.getStatus()).isEqualTo(JobStatus.FAILED);
        assertThat(staleJob.getFinishedAt()).isNotNull();
        assertThat(staleJob.getErrorMessage()).contains("Worker [dead-worker-2] timed out and job exhausted max retries (3)");
        verify(jobRepository).save(staleJob);
    }

    @Test
    @DisplayName("recoverStaleJobs returns zero when no jobs timed out")
    void testRecoverStaleJobsNone() {
        when(jobRepository.findByStatusAndHeartbeatAtBefore(eq(JobStatus.RUNNING), any()))
                .thenReturn(Collections.emptyList());
        when(jobRepository.findByStatusAndHeartbeatAtIsNullAndClaimedAtBefore(eq(JobStatus.RUNNING), any()))
                .thenReturn(Collections.emptyList());

        int recovered = jobService.recoverStaleJobs(Duration.ofMinutes(5));

        assertThat(recovered).isEqualTo(0);
        verify(jobRepository, never()).save(any());
    }

    @Test
    @DisplayName("recoverStaleJobs throws BadRequestException for null or invalid threshold")
    void testRecoverStaleJobsInvalidThreshold() {
        assertThatThrownBy(() -> jobService.recoverStaleJobs(null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Stale threshold must be a positive duration");

        assertThatThrownBy(() -> jobService.recoverStaleJobs(Duration.ofSeconds(-10)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("getJobResponse maps entity to DTO with duration calculation")
    void testGetJobResponse() {
        Instant claimed = Instant.now().minusSeconds(120);
        Instant finished = Instant.now().minusSeconds(30);

        DeploymentJob job = DeploymentJob.builder()
                .id(jobId)
                .deployment(sampleDeployment)
                .type(JobType.DEPLOYMENT)
                .status(JobStatus.COMPLETED)
                .priority(1)
                .retryCount(1)
                .maxRetries(3)
                .claimedBy("worker-alpha")
                .claimedAt(claimed)
                .finishedAt(finished)
                .build();

        when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));

        JobResponse response = jobService.getJobResponse(jobId);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(jobId);
        assertThat(response.getDeploymentId()).isEqualTo(deploymentId);
        assertThat(response.getProjectId()).isEqualTo(sampleDeployment.getProject().getId());
        assertThat(response.getStatus()).isEqualTo(JobStatus.COMPLETED);
        assertThat(response.getDurationSeconds()).isEqualTo(90L);
    }

    @Test
    @DisplayName("getJobForUser returns JobResponse when job belongs to user's project")
    void testGetJobForUser_Success() {
        UUID userId = UUID.randomUUID();
        DeploymentJob job = DeploymentJob.builder()
                .id(jobId)
                .deployment(sampleDeployment)
                .type(JobType.DEPLOYMENT)
                .status(JobStatus.RUNNING)
                .priority(0)
                .build();

        when(jobRepository.findByIdAndUserId(jobId, userId)).thenReturn(Optional.of(job));

        JobResponse response = jobService.getJobForUser(userId, jobId);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(jobId);
        assertThat(response.getStatus()).isEqualTo(JobStatus.RUNNING);
        verify(jobRepository).findByIdAndUserId(jobId, userId);
    }

    @Test
    @DisplayName("getJobForUser throws ResourceNotFoundException when job does not exist or belong to user")
    void testGetJobForUser_NotFound() {
        UUID userId = UUID.randomUUID();
        when(jobRepository.findByIdAndUserId(jobId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> jobService.getJobForUser(userId, jobId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("DeploymentJob");
    }

    @Test
    @DisplayName("getJobForUser throws BadRequestException on null arguments")
    void testGetJobForUser_NullArguments() {
        assertThatThrownBy(() -> jobService.getJobForUser(null, jobId))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> jobService.getJobForUser(UUID.randomUUID(), null))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("getLatestJobForDeploymentAndUser returns latest job")
    void testGetLatestJobForDeploymentAndUser_Success() {
        UUID userId = UUID.randomUUID();
        DeploymentJob job1 = DeploymentJob.builder()
                .id(UUID.randomUUID())
                .deployment(sampleDeployment)
                .type(JobType.DEPLOYMENT)
                .status(JobStatus.COMPLETED)
                .build();
        DeploymentJob job2 = DeploymentJob.builder()
                .id(jobId)
                .deployment(sampleDeployment)
                .type(JobType.DEPLOYMENT)
                .status(JobStatus.RUNNING)
                .build();

        when(jobRepository.findAllByDeploymentIdAndUserIdOrderByCreatedAtDesc(deploymentId, userId))
                .thenReturn(List.of(job2, job1));

        JobResponse response = jobService.getLatestJobForDeploymentAndUser(userId, deploymentId);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(jobId);
        assertThat(response.getStatus()).isEqualTo(JobStatus.RUNNING);
    }

    @Test
    @DisplayName("getLatestJobForDeploymentAndUser throws ResourceNotFoundException when no jobs found")
    void testGetLatestJobForDeploymentAndUser_NotFound() {
        UUID userId = UUID.randomUUID();
        when(jobRepository.findAllByDeploymentIdAndUserIdOrderByCreatedAtDesc(deploymentId, userId))
                .thenReturn(Collections.emptyList());

        assertThatThrownBy(() -> jobService.getLatestJobForDeploymentAndUser(userId, deploymentId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("No background job found for deployment");
    }

    @Test
    @DisplayName("getAllJobsForDeploymentAndUser returns list of jobs")
    void testGetAllJobsForDeploymentAndUser_Success() {
        UUID userId = UUID.randomUUID();
        DeploymentJob job = DeploymentJob.builder()
                .id(jobId)
                .deployment(sampleDeployment)
                .type(JobType.DEPLOYMENT)
                .status(JobStatus.COMPLETED)
                .build();

        when(jobRepository.findAllByDeploymentIdAndUserIdOrderByCreatedAtDesc(deploymentId, userId))
                .thenReturn(List.of(job));

        List<JobResponse> responses = jobService.getAllJobsForDeploymentAndUser(userId, deploymentId);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).getId()).isEqualTo(jobId);
    }

    @Test
    @DisplayName("getQueueStats aggregates counts across all job statuses")
    void testGetQueueStats() {
        when(jobRepository.countByStatus(JobStatus.PENDING)).thenReturn(5L);
        when(jobRepository.countByStatus(JobStatus.RUNNING)).thenReturn(2L);
        when(jobRepository.countByStatus(JobStatus.COMPLETED)).thenReturn(40L);
        when(jobRepository.countByStatus(JobStatus.FAILED)).thenReturn(3L);
        when(jobRepository.countByStatus(JobStatus.CANCELLED)).thenReturn(1L);

        JobQueueStatsResponse stats = jobService.getQueueStats();

        assertThat(stats).isNotNull();
        assertThat(stats.getPendingCount()).isEqualTo(5L);
        assertThat(stats.getRunningCount()).isEqualTo(2L);
        assertThat(stats.getCompletedCount()).isEqualTo(40L);
        assertThat(stats.getFailedCount()).isEqualTo(3L);
        assertThat(stats.getCancelledCount()).isEqualTo(1L);
        assertThat(stats.getTotalCount()).isEqualTo(51L);
    }
}

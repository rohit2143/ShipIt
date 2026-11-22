package com.shipit.backend.job.worker;

import com.shipit.backend.job.config.DeploymentWorkerProperties;
import com.shipit.backend.job.entity.DeploymentJob;
import com.shipit.backend.job.entity.JobStatus;
import com.shipit.backend.job.service.DeploymentJobService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeploymentWorkerTest {

    @Mock
    private DeploymentJobService jobService;

    @Mock
    private DeploymentTaskHandler taskHandler;

    private DeploymentWorkerProperties properties;
    private DeploymentWorker worker;

    @BeforeEach
    void setUp() {
        properties = new DeploymentWorkerProperties();
        properties.setEnabled(true);
        properties.setWorkerId("worker-test-node");
        properties.setStaleThresholdSeconds(180L);

        worker = new DeploymentWorker(jobService, taskHandler, properties);
        worker.init();
    }

    @Test
    @DisplayName("init - Configures custom workerId and sets running to true")
    void init_CustomWorkerId() {
        assertThat(worker.getWorkerId()).isEqualTo("worker-test-node");
        assertThat(worker.isRunning()).isTrue();
    }

    @Test
    @DisplayName("init - Auto-generates workerId when blank in properties")
    void init_BlankWorkerId_GeneratesRandomId() {
        DeploymentWorkerProperties blankProps = new DeploymentWorkerProperties();
        blankProps.setWorkerId("");
        DeploymentWorker autoWorker = new DeploymentWorker(jobService, taskHandler, blankProps);

        autoWorker.init();

        assertThat(autoWorker.getWorkerId()).startsWith("worker-");
        assertThat(autoWorker.isRunning()).isTrue();
    }

    @Test
    @DisplayName("pollAndExecute - Does nothing when worker is disabled")
    void pollAndExecute_Disabled_DoesNothing() {
        properties.setEnabled(false);

        worker.pollAndExecute();

        verifyNoInteractions(jobService);
        verifyNoInteractions(taskHandler);
    }

    @Test
    @DisplayName("pollAndExecute - Does nothing when worker is stopped")
    void pollAndExecute_Stopped_DoesNothing() {
        worker.shutdown();

        worker.pollAndExecute();

        verifyNoInteractions(jobService);
        verifyNoInteractions(taskHandler);
    }

    @Test
    @DisplayName("pollAndExecute - Does nothing when no pending jobs exist")
    void pollAndExecute_NoPendingJob() {
        when(jobService.claimNextJob(worker.getWorkerId())).thenReturn(Optional.empty());

        worker.pollAndExecute();

        verify(jobService).claimNextJob(worker.getWorkerId());
        verifyNoInteractions(taskHandler);
    }

    @Test
    @DisplayName("pollAndExecute - Successfully processes claimed job and completes it")
    void pollAndExecute_Success() {
        UUID jobId = UUID.randomUUID();
        DeploymentJob job = DeploymentJob.builder().id(jobId).status(JobStatus.RUNNING).build();

        when(jobService.claimNextJob(worker.getWorkerId())).thenReturn(Optional.of(job));
        when(jobService.getJobEntity(jobId)).thenReturn(job);

        worker.pollAndExecute();

        verify(jobService).claimNextJob(worker.getWorkerId());
        verify(taskHandler).handleDeployment(job);
        verify(jobService).completeJob(jobId);
        verify(jobService, never()).failJob(any(), any(), anyBoolean());
    }

    @Test
    @DisplayName("pollAndExecute - Calls failJob when task handler throws exception")
    void pollAndExecute_HandlerFails_CallsFailJob() {
        UUID jobId = UUID.randomUUID();
        DeploymentJob job = DeploymentJob.builder().id(jobId).status(JobStatus.RUNNING).build();

        when(jobService.claimNextJob(worker.getWorkerId())).thenReturn(Optional.of(job));
        doThrow(new RuntimeException("Docker daemon not running")).when(taskHandler).handleDeployment(job);
        when(jobService.getJobEntity(jobId)).thenReturn(job);

        worker.pollAndExecute();

        verify(taskHandler).handleDeployment(job);
        verify(jobService, never()).completeJob(jobId);
        verify(jobService).failJob(jobId, "Docker daemon not running", true);
    }

    @Test
    @DisplayName("pollAndExecute - Skips completion if job was cancelled while in-flight")
    void pollAndExecute_JobCancelledMidway_SkipsCompletion() {
        UUID jobId = UUID.randomUUID();
        DeploymentJob job = DeploymentJob.builder().id(jobId).status(JobStatus.RUNNING).build();
        DeploymentJob cancelledJob = DeploymentJob.builder().id(jobId).status(JobStatus.CANCELLED).build();

        when(jobService.claimNextJob(worker.getWorkerId())).thenReturn(Optional.of(job));
        when(jobService.getJobEntity(jobId)).thenReturn(cancelledJob);

        worker.pollAndExecute();

        verify(taskHandler).handleDeployment(job);
        verify(jobService, never()).completeJob(any());
        verify(jobService, never()).failJob(any(), any(), anyBoolean());
    }

    @Test
    @DisplayName("recoverStaleJobs - Calls recoverStaleJobs on service with configured threshold")
    void recoverStaleJobs_Success() {
        when(jobService.recoverStaleJobs(Duration.ofSeconds(180))).thenReturn(2);

        worker.recoverStaleJobs();

        verify(jobService).recoverStaleJobs(Duration.ofSeconds(180));
    }

    @Test
    @DisplayName("recoverStaleJobs - Does nothing when worker is disabled")
    void recoverStaleJobs_Disabled_DoesNothing() {
        properties.setEnabled(false);

        worker.recoverStaleJobs();

        verifyNoInteractions(jobService);
    }

    @Test
    @DisplayName("shutdown - Sets running to false")
    void shutdown_SetsRunningFalse() {
        assertThat(worker.isRunning()).isTrue();

        worker.shutdown();

        assertThat(worker.isRunning()).isFalse();
    }
}

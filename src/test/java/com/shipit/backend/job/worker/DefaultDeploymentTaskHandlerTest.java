package com.shipit.backend.job.worker;

import com.shipit.backend.deployment.entity.Deployment;
import com.shipit.backend.deployment.entity.DeploymentStatus;
import com.shipit.backend.deployment.repository.DeploymentRepository;
import com.shipit.backend.deployment.service.DeploymentService;
import com.shipit.backend.job.entity.DeploymentJob;
import com.shipit.backend.job.service.DeploymentJobService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DefaultDeploymentTaskHandlerTest {

    @Mock
    private DeploymentRepository deploymentRepository;

    @Mock
    private DeploymentService deploymentService;

    @Mock
    private DeploymentJobService jobService;

    @InjectMocks
    private DefaultDeploymentTaskHandler taskHandler;

    private UUID deploymentId;
    private UUID jobId;
    private Deployment sampleDeployment;
    private DeploymentJob sampleJob;

    @BeforeEach
    void setUp() {
        deploymentId = UUID.randomUUID();
        jobId = UUID.randomUUID();

        sampleDeployment = Deployment.builder()
                .id(deploymentId)
                .status(DeploymentStatus.QUEUED)
                .build();

        sampleJob = DeploymentJob.builder()
                .id(jobId)
                .deployment(sampleDeployment)
                .build();
    }

    @Test
    @DisplayName("handleDeployment - Successfully transitions through all lifecycle states to SUCCESS")
    void handleDeployment_Success() {
        when(deploymentRepository.findById(deploymentId)).thenReturn(Optional.of(sampleDeployment));

        taskHandler.handleDeployment(sampleJob);

        verify(deploymentService).transitionStatus(deploymentId, DeploymentStatus.CLONING, null);
        verify(deploymentService).transitionStatus(deploymentId, DeploymentStatus.DETECTING, null);
        verify(deploymentService).transitionStatus(deploymentId, DeploymentStatus.BUILDING, null);
        verify(deploymentService).transitionStatus(deploymentId, DeploymentStatus.STARTING, null);
        verify(deploymentService).transitionStatus(deploymentId, DeploymentStatus.HEALTH_CHECKING, null);
        verify(deploymentService).transitionStatus(deploymentId, DeploymentStatus.SUCCESS, null);

        verify(jobService, times(5)).recordHeartbeat(jobId);
    }

    @Test
    @DisplayName("handleDeployment - Null job or deployment aborts gracefully without error")
    void handleDeployment_NullJobOrDeployment() {
        taskHandler.handleDeployment(null);
        taskHandler.handleDeployment(DeploymentJob.builder().deployment(null).build());

        verifyNoInteractions(deploymentRepository);
        verifyNoInteractions(deploymentService);
        verifyNoInteractions(jobService);
    }

    @Test
    @DisplayName("handleDeployment - Deployment not found in database aborts gracefully")
    void handleDeployment_DeploymentNotFound() {
        when(deploymentRepository.findById(deploymentId)).thenReturn(Optional.empty());

        taskHandler.handleDeployment(sampleJob);

        verify(deploymentRepository).findById(deploymentId);
        verifyNoInteractions(deploymentService);
        verifyNoInteractions(jobService);
    }

    @Test
    @DisplayName("handleDeployment - Deployment already CANCELLED aborts before starting transitions")
    void handleDeployment_AlreadyCancelled() {
        sampleDeployment.setStatus(DeploymentStatus.CANCELLED);
        when(deploymentRepository.findById(deploymentId)).thenReturn(Optional.of(sampleDeployment));

        taskHandler.handleDeployment(sampleJob);

        verify(deploymentRepository).findById(deploymentId);
        verifyNoInteractions(deploymentService);
        verifyNoInteractions(jobService);
    }

    @Test
    @DisplayName("handleDeployment - Cancellation midway halts execution and throws IllegalStateException")
    void handleDeployment_CancelledMidway() {
        Deployment activeDep = Deployment.builder().id(deploymentId).status(DeploymentStatus.QUEUED).build();
        Deployment cancelledDep = Deployment.builder().id(deploymentId).status(DeploymentStatus.CANCELLED).build();

        // 1st lookup: initial check -> active
        // 2nd lookup: step 1 -> active
        // 3rd lookup: step 2 -> cancelled
        when(deploymentRepository.findById(deploymentId))
                .thenReturn(Optional.of(activeDep))
                .thenReturn(Optional.of(activeDep))
                .thenReturn(Optional.of(cancelledDep));

        taskHandler.handleDeployment(sampleJob);

        verify(deploymentService).transitionStatus(deploymentId, DeploymentStatus.CLONING, null);
        verify(deploymentService, never()).transitionStatus(deploymentId, DeploymentStatus.DETECTING, null);
        verify(deploymentService, never()).transitionStatus(deploymentId, DeploymentStatus.SUCCESS, null);
    }

    @Test
    @DisplayName("handleDeployment - Exception during transition marks deployment FAILED and rethrows")
    void handleDeployment_FailureMarksDeploymentFailed() {
        when(deploymentRepository.findById(deploymentId)).thenReturn(Optional.of(sampleDeployment));
        doThrow(new RuntimeException("Build error: out of disk space"))
                .when(deploymentService).transitionStatus(deploymentId, DeploymentStatus.BUILDING, null);

        assertThatThrownBy(() -> taskHandler.handleDeployment(sampleJob))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Build error: out of disk space");

        verify(deploymentService).transitionStatus(deploymentId, DeploymentStatus.FAILED, "Build error: out of disk space");
    }
}

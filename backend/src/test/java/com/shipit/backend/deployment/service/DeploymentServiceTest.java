package com.shipit.backend.deployment.service;

import com.shipit.backend.common.exception.BadRequestException;
import com.shipit.backend.common.exception.ConflictException;
import com.shipit.backend.common.exception.ResourceNotFoundException;
import com.shipit.backend.deployment.dto.DeploymentResponse;
import com.shipit.backend.deployment.dto.DeploymentSummaryResponse;
import com.shipit.backend.deployment.dto.TriggerDeploymentRequest;
import com.shipit.backend.deployment.entity.Deployment;
import com.shipit.backend.deployment.entity.DeploymentStatus;
import com.shipit.backend.deployment.entity.DeploymentTrigger;
import com.shipit.backend.deployment.repository.DeploymentRepository;
import com.shipit.backend.job.entity.DeploymentJob;
import com.shipit.backend.job.entity.JobStatus;
import com.shipit.backend.job.service.DeploymentJobService;
import com.shipit.backend.project.entity.Project;
import com.shipit.backend.project.entity.ProjectStatus;
import com.shipit.backend.project.repository.ProjectRepository;
import com.shipit.backend.user.entity.Role;
import com.shipit.backend.user.entity.User;
import com.shipit.backend.user.entity.UserStatus;
import com.shipit.backend.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeploymentServiceTest {

    @Mock
    private DeploymentRepository deploymentRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private UserService userService;

    @Mock
    private DeploymentJobService deploymentJobService;

    @InjectMocks
    private DeploymentService deploymentService;

    private User sampleUser;
    private Project sampleProject;
    private UUID userId;
    private UUID projectId;
    private UUID deploymentId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        projectId = UUID.randomUUID();
        deploymentId = UUID.randomUUID();

        sampleUser = User.builder()
                .id(userId)
                .name("Rohit Kumar")
                .email("rohit@example.com")
                .role(Role.ROLE_USER)
                .status(UserStatus.ACTIVE)
                .build();

        sampleProject = Project.builder()
                .id(projectId)
                .user(sampleUser)
                .name("ShipIt Backend")
                .deploymentBranch("main")
                .port(8080)
                .status(ProjectStatus.ACTIVE)
                .build();
    }

    @Test
    @DisplayName("triggerManualDeployment - Success with default project branch and empty request")
    void triggerManualDeployment_Success_DefaultBranch() {
        when(userService.getById(userId)).thenReturn(sampleUser);
        when(projectRepository.findByIdAndUserId(projectId, userId)).thenReturn(Optional.of(sampleProject));
        when(deploymentRepository.save(any(Deployment.class))).thenAnswer(inv -> {
            Deployment d = inv.getArgument(0);
            d.setId(deploymentId);
            return d;
        });

        DeploymentResponse response = deploymentService.triggerManualDeployment(userId, projectId, null);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(deploymentId);
        assertThat(response.getProjectId()).isEqualTo(projectId);
        assertThat(response.getProjectName()).isEqualTo("ShipIt Backend");
        assertThat(response.getStatus()).isEqualTo(DeploymentStatus.QUEUED);
        assertThat(response.getTrigger()).isEqualTo(DeploymentTrigger.MANUAL);
        assertThat(response.getCommitBranch()).isEqualTo("main");
        assertThat(response.getCommitAuthor()).isEqualTo("Rohit Kumar");
        assertThat(response.getQueuedAt()).isNotNull();

        ArgumentCaptor<Deployment> captor = ArgumentCaptor.forClass(Deployment.class);
        verify(deploymentRepository).save(captor.capture());
        Deployment saved = captor.getValue();
        assertThat(saved.getCommitBranch()).isEqualTo("main");

        verify(deploymentJobService).createJob(eq(saved), eq(0));
    }

    @Test
    @DisplayName("triggerManualDeployment - Success with custom branch, commitSha and commitMessage")
    void triggerManualDeployment_Success_CustomRequest() {
        TriggerDeploymentRequest request = TriggerDeploymentRequest.builder()
                .branch("release/v2")
                .commitSha("abcdef1234567890abcdef1234567890abcdef12")
                .commitMessage("feat: release v2 with new architecture")
                .build();

        when(userService.getById(userId)).thenReturn(sampleUser);
        when(projectRepository.findByIdAndUserId(projectId, userId)).thenReturn(Optional.of(sampleProject));
        when(deploymentRepository.save(any(Deployment.class))).thenAnswer(inv -> {
            Deployment d = inv.getArgument(0);
            d.setId(deploymentId);
            return d;
        });

        DeploymentResponse response = deploymentService.triggerManualDeployment(userId, projectId, request);

        assertThat(response.getCommitBranch()).isEqualTo("release/v2");
        assertThat(response.getCommitSha()).isEqualTo("abcdef1234567890abcdef1234567890abcdef12");
        assertThat(response.getCommitMessage()).isEqualTo("feat: release v2 with new architecture");

        verify(deploymentJobService).createJob(any(Deployment.class), eq(0));
    }

    @Test
    @DisplayName("triggerManualDeployment - Project not found throws ResourceNotFoundException")
    void triggerManualDeployment_ProjectNotFound_ThrowsResourceNotFoundException() {
        when(userService.getById(userId)).thenReturn(sampleUser);
        when(projectRepository.findByIdAndUserId(projectId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> deploymentService.triggerManualDeployment(userId, projectId, null))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Project not found with id: " + projectId);

        verify(deploymentRepository, never()).save(any());
        verifyNoInteractions(deploymentJobService);
    }

    @Test
    @DisplayName("triggerManualDeployment - Project not ACTIVE throws BadRequestException")
    void triggerManualDeployment_ProjectNotActive_ThrowsBadRequestException() {
        sampleProject.setStatus(ProjectStatus.ARCHIVED);

        when(userService.getById(userId)).thenReturn(sampleUser);
        when(projectRepository.findByIdAndUserId(projectId, userId)).thenReturn(Optional.of(sampleProject));

        assertThatThrownBy(() -> deploymentService.triggerManualDeployment(userId, projectId, null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Cannot deploy project with status: ARCHIVED");

        verify(deploymentRepository, never()).save(any());
        verifyNoInteractions(deploymentJobService);
    }

    @Test
    @DisplayName("getProjectDeployments - Success returns paginated summaries")
    void getProjectDeployments_Success() {
        Deployment d1 = Deployment.builder()
                .id(UUID.randomUUID())
                .project(sampleProject)
                .status(DeploymentStatus.SUCCESS)
                .trigger(DeploymentTrigger.MANUAL)
                .commitBranch("main")
                .commitSha("sha1")
                .queuedAt(Instant.now())
                .startedAt(Instant.now())
                .finishedAt(Instant.now().plusSeconds(30))
                .build();

        PageRequest pageRequest = PageRequest.of(0, 10);
        when(projectRepository.findByIdAndUserId(projectId, userId)).thenReturn(Optional.of(sampleProject));
        when(deploymentRepository.findAllByProjectId(projectId, pageRequest))
                .thenReturn(new PageImpl<>(List.of(d1), pageRequest, 1));

        Page<DeploymentSummaryResponse> page = deploymentService.getProjectDeployments(userId, projectId, pageRequest);

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().get(0).getCommitSha()).isEqualTo("sha1");
        assertThat(page.getContent().get(0).getStatus()).isEqualTo(DeploymentStatus.SUCCESS);
    }

    @Test
    @DisplayName("getProjectDeployments - Project not found throws ResourceNotFoundException")
    void getProjectDeployments_ProjectNotFound_ThrowsException() {
        when(projectRepository.findByIdAndUserId(projectId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> deploymentService.getProjectDeployments(userId, projectId, PageRequest.of(0, 10)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Project not found with id: " + projectId);
    }

    @Test
    @DisplayName("getDeployment - Success returns deployment response")
    void getDeployment_Success() {
        Deployment d = Deployment.builder()
                .id(deploymentId)
                .project(sampleProject)
                .status(DeploymentStatus.BUILDING)
                .trigger(DeploymentTrigger.MANUAL)
                .commitBranch("main")
                .commitSha("abc123")
                .queuedAt(Instant.now().minusSeconds(10))
                .startedAt(Instant.now().minusSeconds(5))
                .build();

        when(deploymentRepository.findByIdAndUserId(deploymentId, userId)).thenReturn(Optional.of(d));

        DeploymentResponse response = deploymentService.getDeployment(userId, deploymentId);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(deploymentId);
        assertThat(response.getStatus()).isEqualTo(DeploymentStatus.BUILDING);
    }

    @Test
    @DisplayName("getDeployment - Not found throws ResourceNotFoundException")
    void getDeployment_NotFound_ThrowsException() {
        when(deploymentRepository.findByIdAndUserId(deploymentId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> deploymentService.getDeployment(userId, deploymentId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Deployment not found with id: " + deploymentId);
    }

    @Test
    @DisplayName("cancelDeployment - Success when status is QUEUED and cancels active job")
    void cancelDeployment_Success_FromQueued() {
        Deployment d = Deployment.builder()
                .id(deploymentId)
                .project(sampleProject)
                .status(DeploymentStatus.QUEUED)
                .trigger(DeploymentTrigger.MANUAL)
                .commitBranch("main")
                .queuedAt(Instant.now())
                .build();

        UUID jobId = UUID.randomUUID();
        DeploymentJob job = DeploymentJob.builder().id(jobId).status(JobStatus.PENDING).build();

        when(deploymentRepository.findByIdAndUserId(deploymentId, userId)).thenReturn(Optional.of(d));
        when(deploymentRepository.save(any(Deployment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(deploymentJobService.getLatestJobForDeployment(deploymentId)).thenReturn(Optional.of(job));

        DeploymentResponse response = deploymentService.cancelDeployment(userId, deploymentId);

        assertThat(response.getStatus()).isEqualTo(DeploymentStatus.CANCELLED);
        assertThat(response.getFinishedAt()).isNotNull();

        verify(deploymentJobService).cancelJob(jobId);
    }

    @Test
    @DisplayName("cancelDeployment - Success when status is CLONING")
    void cancelDeployment_Success_FromCloning() {
        Deployment d = Deployment.builder()
                .id(deploymentId)
                .project(sampleProject)
                .status(DeploymentStatus.CLONING)
                .trigger(DeploymentTrigger.MANUAL)
                .commitBranch("main")
                .queuedAt(Instant.now())
                .startedAt(Instant.now())
                .build();

        when(deploymentRepository.findByIdAndUserId(deploymentId, userId)).thenReturn(Optional.of(d));
        when(deploymentRepository.save(any(Deployment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(deploymentJobService.getLatestJobForDeployment(deploymentId)).thenReturn(Optional.empty());

        DeploymentResponse response = deploymentService.cancelDeployment(userId, deploymentId);

        assertThat(response.getStatus()).isEqualTo(DeploymentStatus.CANCELLED);
        assertThat(response.getFinishedAt()).isNotNull();

        verify(deploymentJobService, never()).cancelJob(any());
    }

    @Test
    @DisplayName("cancelDeployment - Already terminal throws ConflictException")
    void cancelDeployment_AlreadyTerminal_ThrowsConflictException() {
        Deployment d = Deployment.builder()
                .id(deploymentId)
                .project(sampleProject)
                .status(DeploymentStatus.SUCCESS)
                .build();

        when(deploymentRepository.findByIdAndUserId(deploymentId, userId)).thenReturn(Optional.of(d));

        assertThatThrownBy(() -> deploymentService.cancelDeployment(userId, deploymentId))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Deployment is already in terminal state: SUCCESS");

        verify(deploymentRepository, never()).save(any());
        verifyNoInteractions(deploymentJobService);
    }

    @Test
    @DisplayName("cancelDeployment - Active in-flight build throws ConflictException")
    void cancelDeployment_InFlightBuild_ThrowsConflictException() {
        Deployment d = Deployment.builder()
                .id(deploymentId)
                .project(sampleProject)
                .status(DeploymentStatus.BUILDING)
                .build();

        when(deploymentRepository.findByIdAndUserId(deploymentId, userId)).thenReturn(Optional.of(d));

        assertThatThrownBy(() -> deploymentService.cancelDeployment(userId, deploymentId))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Cannot cancel deployment in BUILDING status");

        verify(deploymentRepository, never()).save(any());
        verifyNoInteractions(deploymentJobService);
    }

    @Test
    @DisplayName("retryDeployment - Success from FAILED deployment enqueues background job")
    void retryDeployment_Success_FromFailed() {
        Deployment failed = Deployment.builder()
                .id(deploymentId)
                .project(sampleProject)
                .status(DeploymentStatus.FAILED)
                .trigger(DeploymentTrigger.MANUAL)
                .commitBranch("develop")
                .commitSha("failedSha123")
                .commitMessage("Initial fix")
                .commitAuthor("Rohit Kumar")
                .build();

        when(userService.getById(userId)).thenReturn(sampleUser);
        when(deploymentRepository.findByIdAndUserId(deploymentId, userId)).thenReturn(Optional.of(failed));
        when(deploymentRepository.save(any(Deployment.class))).thenAnswer(inv -> {
            Deployment d = inv.getArgument(0);
            d.setId(UUID.randomUUID());
            return d;
        });

        DeploymentResponse response = deploymentService.retryDeployment(userId, deploymentId);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(DeploymentStatus.QUEUED);
        assertThat(response.getTrigger()).isEqualTo(DeploymentTrigger.RETRY);
        assertThat(response.getCommitBranch()).isEqualTo("develop");
        assertThat(response.getCommitSha()).isEqualTo("failedSha123");
        assertThat(response.getCommitMessage()).isEqualTo("Retry: Initial fix");

        verify(deploymentJobService).createJob(any(Deployment.class), eq(0));
    }

    @Test
    @DisplayName("retryDeployment - Retrying SUCCESS deployment throws BadRequestException")
    void retryDeployment_SuccessStatus_ThrowsBadRequestException() {
        Deployment successDep = Deployment.builder()
                .id(deploymentId)
                .project(sampleProject)
                .status(DeploymentStatus.SUCCESS)
                .build();

        when(userService.getById(userId)).thenReturn(sampleUser);
        when(deploymentRepository.findByIdAndUserId(deploymentId, userId)).thenReturn(Optional.of(successDep));

        assertThatThrownBy(() -> deploymentService.retryDeployment(userId, deploymentId))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Only failed or cancelled deployments can be retried");

        verify(deploymentRepository, never()).save(any());
        verifyNoInteractions(deploymentJobService);
    }

    @Test
    @DisplayName("retryDeployment - Project not active throws BadRequestException")
    void retryDeployment_ProjectNotActive_ThrowsBadRequestException() {
        sampleProject.setStatus(ProjectStatus.ARCHIVED);
        Deployment failed = Deployment.builder()
                .id(deploymentId)
                .project(sampleProject)
                .status(DeploymentStatus.FAILED)
                .build();

        when(userService.getById(userId)).thenReturn(sampleUser);
        when(deploymentRepository.findByIdAndUserId(deploymentId, userId)).thenReturn(Optional.of(failed));

        assertThatThrownBy(() -> deploymentService.retryDeployment(userId, deploymentId))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Cannot retry deployment for project with status: ARCHIVED");

        verify(deploymentRepository, never()).save(any());
        verifyNoInteractions(deploymentJobService);
    }

    @Test
    @DisplayName("transitionStatus - Valid transition updates status")
    void transitionStatus_ValidTransition_UpdatesStatus() {
        Deployment d = Deployment.builder()
                .id(deploymentId)
                .status(DeploymentStatus.QUEUED)
                .build();

        when(deploymentRepository.findById(deploymentId)).thenReturn(Optional.of(d));
        when(deploymentRepository.save(any(Deployment.class))).thenAnswer(inv -> inv.getArgument(0));

        Deployment result = deploymentService.transitionStatus(deploymentId, DeploymentStatus.CLONING, null);

        assertThat(result.getStatus()).isEqualTo(DeploymentStatus.CLONING);
        assertThat(result.getStartedAt()).isNotNull();
    }

    @Test
    @DisplayName("transitionStatus - FAILED transition records error message")
    void transitionStatus_Failed_RecordsErrorMessage() {
        Deployment d = Deployment.builder()
                .id(deploymentId)
                .status(DeploymentStatus.BUILDING)
                .build();

        when(deploymentRepository.findById(deploymentId)).thenReturn(Optional.of(d));
        when(deploymentRepository.save(any(Deployment.class))).thenAnswer(inv -> inv.getArgument(0));

        Deployment result = deploymentService.transitionStatus(deploymentId, DeploymentStatus.FAILED, "Docker build exited with code 1");

        assertThat(result.getStatus()).isEqualTo(DeploymentStatus.FAILED);
        assertThat(result.getErrorMessage()).isEqualTo("Docker build exited with code 1");
        assertThat(result.getFinishedAt()).isNotNull();
    }
}

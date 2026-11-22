package com.shipit.backend.job.worker;

import com.shipit.backend.build.config.BuildProperties;
import com.shipit.backend.build.detector.ProjectDetector;
import com.shipit.backend.build.generator.DockerfileGenerator;
import com.shipit.backend.build.model.BuildPlan;
import com.shipit.backend.build.model.BuildResult;
import com.shipit.backend.build.model.ProjectType;
import com.shipit.backend.build.service.BuildLogService;
import com.shipit.backend.build.service.DockerBuildService;
import com.shipit.backend.build.service.GitCloneService;
import com.shipit.backend.build.workspace.WorkspaceManager;
import com.shipit.backend.container.config.ContainerProperties;
import com.shipit.backend.container.exception.ContainerException;
import com.shipit.backend.container.health.ContainerHealthChecker;
import com.shipit.backend.container.model.ContainerConfig;
import com.shipit.backend.container.port.PortAllocator;
import com.shipit.backend.container.service.DockerContainerService;
import com.shipit.backend.deployment.entity.Deployment;
import com.shipit.backend.deployment.entity.DeploymentStatus;
import com.shipit.backend.deployment.repository.DeploymentRepository;
import com.shipit.backend.deployment.service.DeploymentService;
import com.shipit.backend.job.entity.DeploymentJob;
import com.shipit.backend.job.service.DeploymentJobService;
import com.shipit.backend.log.service.DeploymentLogService;
import com.shipit.backend.project.entity.Project;
import com.shipit.backend.project.service.ProjectEnvironmentVariableService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
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

    @Mock
    private GitCloneService gitCloneService;

    @Mock
    private ProjectDetector projectDetector;

    @Mock
    private DockerfileGenerator dockerfileGenerator;

    @Mock
    private DockerBuildService dockerBuildService;

    @Mock
    private WorkspaceManager workspaceManager;

    @Mock
    private BuildLogService buildLogService;

    @Mock
    private BuildProperties buildProperties;

    @Mock
    private DockerContainerService dockerContainerService;

    @Mock
    private PortAllocator portAllocator;

    @Mock
    private ContainerHealthChecker containerHealthChecker;

    @Mock
    private ProjectEnvironmentVariableService projectEnvironmentVariableService;

    @Mock
    private ContainerProperties containerProperties;

    @Mock
    private DeploymentLogService deploymentLogService;

    @InjectMocks
    private DefaultDeploymentTaskHandler taskHandler;

    private UUID deploymentId;
    private UUID jobId;
    private Deployment sampleDeployment;
    private DeploymentJob sampleJob;
    private Project sampleProject;
    private Path sampleWorkspace;
    private BuildPlan sampleBuildPlan;
    private BuildResult sampleBuildResult;

    @BeforeEach
    void setUp() {
        deploymentId = UUID.randomUUID();
        jobId = UUID.randomUUID();

        sampleProject = Project.builder()
                .id(UUID.randomUUID())
                .name("sample-app")
                .normalizedName("sample-app")
                .port(8080)
                .build();

        sampleDeployment = Deployment.builder()
                .id(deploymentId)
                .project(sampleProject)
                .commitBranch("main")
                .commitSha("abc1234567890abcdef1234567890abcdef12")
                .status(DeploymentStatus.QUEUED)
                .build();

        sampleJob = DeploymentJob.builder()
                .id(jobId)
                .deployment(sampleDeployment)
                .build();

        sampleWorkspace = Path.of("/tmp/shipit-builds/test-source");

        sampleBuildPlan = BuildPlan.builder()
                .projectId(sampleProject.getId())
                .deploymentId(deploymentId)
                .projectType(ProjectType.MAVEN_SPRING_BOOT)
                .rootDirectory("")
                .dockerfilePath(sampleWorkspace.resolve("Dockerfile.shipit").toString())
                .imageTag("shipit/sample-app:abc1234")
                .port(8080)
                .customDockerfile(false)
                .build();

        sampleBuildResult = BuildResult.builder()
                .imageTag("shipit/sample-app:abc1234")
                .imageId("sha256:1122334455")
                .durationSeconds(15L)
                .success(true)
                .build();

        when(buildProperties.getMaxLogLines()).thenReturn(5000);
        when(buildProperties.getMaxLineLength()).thenReturn(1024);

        when(gitCloneService.cloneRepository(any(), any())).thenReturn(sampleWorkspace);
        when(projectDetector.detect(any(), any(), any())).thenReturn(sampleBuildPlan);
        when(dockerfileGenerator.generateDockerfile(any(), any(), any()))
                .thenReturn(sampleWorkspace.resolve("Dockerfile.shipit"));
        when(dockerBuildService.buildImage(any(), any(), any())).thenReturn(sampleBuildResult);
        when(deploymentService.transitionStatus(any(), any(), any())).thenReturn(sampleDeployment);
        when(deploymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Container Mocks default configuration
        when(portAllocator.allocatePort()).thenReturn(10050);
        when(containerProperties.getMemoryLimit()).thenReturn("512m");
        when(containerProperties.getCpuLimit()).thenReturn("0.5");
        when(containerProperties.getRestartPolicy()).thenReturn("unless-stopped");
        when(containerProperties.getHealthCheckPath()).thenReturn("/");
        when(containerProperties.getStopTimeoutSeconds()).thenReturn(10);
        when(projectEnvironmentVariableService.getDecryptedEnvironmentVariablesForDeployment(any()))
                .thenReturn(Map.of("API_KEY", "super-secret-key"));
        when(dockerContainerService.runContainer(any(), any())).thenReturn("c1234567890abcdef");
        when(containerHealthChecker.verifyHealth(any(), anyInt(), any(), any())).thenReturn(true);
        when(deploymentRepository.findByProjectIdAndContainerIdIsNotNullAndIdNot(any(), any()))
                .thenReturn(List.of());
    }

    @Test
    @DisplayName("handleDeployment - Successfully executes clone, detect, generate, build, run container, health check and transitions to SUCCESS")
    void handleDeployment_Success() {
        when(deploymentRepository.findByIdWithDetails(deploymentId)).thenReturn(Optional.of(sampleDeployment));
        when(deploymentRepository.findById(deploymentId)).thenReturn(Optional.of(sampleDeployment));

        taskHandler.handleDeployment(sampleJob);

        // Verify status transitions
        verify(deploymentService).transitionStatus(deploymentId, DeploymentStatus.CLONING, null);
        verify(deploymentService).transitionStatus(deploymentId, DeploymentStatus.DETECTING, null);
        verify(deploymentService).transitionStatus(deploymentId, DeploymentStatus.BUILDING, null);
        verify(deploymentService).transitionStatus(deploymentId, DeploymentStatus.STARTING, null);
        verify(deploymentService).transitionStatus(deploymentId, DeploymentStatus.HEALTH_CHECKING, null);
        verify(deploymentService).transitionStatus(deploymentId, DeploymentStatus.SUCCESS, null);

        // Verify build engine components were invoked
        verify(gitCloneService).cloneRepository(eq(sampleDeployment), any());
        verify(projectDetector).detect(eq(sampleWorkspace), eq(sampleDeployment), any());
        verify(dockerfileGenerator).generateDockerfile(eq(sampleWorkspace), eq(sampleBuildPlan), any());
        verify(dockerBuildService).buildImage(eq(sampleWorkspace), eq(sampleBuildPlan), any());

        // Verify container engine components were invoked
        verify(portAllocator).allocatePort();
        verify(projectEnvironmentVariableService).getDecryptedEnvironmentVariablesForDeployment(sampleProject.getId());

        ArgumentCaptor<ContainerConfig> configCaptor = ArgumentCaptor.forClass(ContainerConfig.class);
        verify(dockerContainerService).runContainer(configCaptor.capture(), any());

        ContainerConfig config = configCaptor.getValue();
        assertThat(config.getDeploymentId()).isEqualTo(deploymentId);
        assertThat(config.getProjectId()).isEqualTo(sampleProject.getId());
        assertThat(config.getHostPort()).isEqualTo(10050);
        assertThat(config.getContainerPort()).isEqualTo(8080);
        assertThat(config.getImageTag()).isEqualTo("shipit/sample-app:abc1234");
        assertThat(config.getContainerName()).isEqualTo("shipit-sample-app-" + deploymentId.toString().substring(0, 8));
        assertThat(config.getMemoryLimit()).isEqualTo("512m");
        assertThat(config.getCpuLimit()).isEqualTo("0.5");
        assertThat(config.getRestartPolicy()).isEqualTo("unless-stopped");
        assertThat(config.getEnvironmentVariables()).containsEntry("API_KEY", "super-secret-key");
        assertThat(config.getEnvironmentVariables()).containsEntry("PORT", "8080");
        assertThat(config.getEnvironmentVariables()).containsEntry("SHIPIT_DEPLOYMENT_ID", deploymentId.toString());
        assertThat(config.getEnvironmentVariables()).containsEntry("SHIPIT_PROJECT_ID", sampleProject.getId().toString());

        // Verify container health check was invoked
        verify(containerHealthChecker).verifyHealth(eq("c1234567890abcdef"), eq(10050), eq("/"), any());

        // Verify container metadata updated on deployment
        assertThat(sampleDeployment.getDockerImageTag()).isEqualTo("shipit/sample-app:abc1234");
        assertThat(sampleDeployment.getContainerId()).isEqualTo("c1234567890abcdef");
        assertThat(sampleDeployment.getContainerPort()).isEqualTo(10050);
        verify(deploymentRepository, atLeast(2)).save(sampleDeployment);

        // Verify cleanup in finally
        verify(buildLogService).storeLogs(eq(deploymentId), any());
        verify(workspaceManager).deleteWorkspace(deploymentId);

        // Verify heartbeat recorded at multiple stages
        verify(jobService, atLeast(6)).recordHeartbeat(jobId);
    }

    @Test
    @DisplayName("handleDeployment - Null job or deployment aborts gracefully without error")
    void handleDeployment_NullJobOrDeployment() {
        taskHandler.handleDeployment(null);
        taskHandler.handleDeployment(DeploymentJob.builder().deployment(null).build());

        verifyNoInteractions(deploymentRepository);
        verifyNoInteractions(deploymentService);
        verifyNoInteractions(jobService);
        verifyNoInteractions(gitCloneService);
        verifyNoInteractions(buildLogService);
    }

    @Test
    @DisplayName("handleDeployment - Deployment not found in database aborts gracefully")
    void handleDeployment_DeploymentNotFound() {
        when(deploymentRepository.findByIdWithDetails(deploymentId)).thenReturn(Optional.empty());
        when(deploymentRepository.findById(deploymentId)).thenReturn(Optional.empty());

        taskHandler.handleDeployment(sampleJob);

        verifyNoInteractions(deploymentService);
        verifyNoInteractions(jobService);
        verifyNoInteractions(gitCloneService);
        verifyNoInteractions(buildLogService);
    }

    @Test
    @DisplayName("handleDeployment - Deployment already CANCELLED aborts before starting transitions")
    void handleDeployment_AlreadyCancelled() {
        sampleDeployment.setStatus(DeploymentStatus.CANCELLED);
        when(deploymentRepository.findByIdWithDetails(deploymentId)).thenReturn(Optional.of(sampleDeployment));

        taskHandler.handleDeployment(sampleJob);

        verifyNoInteractions(deploymentService);
        verifyNoInteractions(jobService);
        verifyNoInteractions(gitCloneService);
        verifyNoInteractions(buildLogService);
    }

    @Test
    @DisplayName("handleDeployment - Cancellation midway halts execution and cleans up workspace")
    void handleDeployment_CancelledMidway() {
        Deployment activeDep = Deployment.builder().id(deploymentId).project(sampleProject).commitBranch("main").status(DeploymentStatus.QUEUED).build();
        Deployment cancelledDep = Deployment.builder().id(deploymentId).project(sampleProject).commitBranch("main").status(DeploymentStatus.CANCELLED).build();

        when(deploymentRepository.findByIdWithDetails(deploymentId)).thenReturn(Optional.of(activeDep));
        when(deploymentRepository.findById(deploymentId))
                .thenReturn(Optional.of(activeDep))
                .thenReturn(Optional.of(cancelledDep));

        taskHandler.handleDeployment(sampleJob);

        verify(deploymentService).transitionStatus(deploymentId, DeploymentStatus.CLONING, null);
        verify(deploymentService, never()).transitionStatus(deploymentId, DeploymentStatus.DETECTING, null);
        verify(deploymentService, never()).transitionStatus(deploymentId, DeploymentStatus.SUCCESS, null);

        // Cleanup still occurs
        verify(buildLogService).storeLogs(eq(deploymentId), any());
        verify(workspaceManager).deleteWorkspace(deploymentId);
    }

    @Test
    @DisplayName("handleDeployment - Failure during Git cloning marks deployment FAILED and stores logs")
    void handleDeployment_FailureInCloning() {
        when(deploymentRepository.findByIdWithDetails(deploymentId)).thenReturn(Optional.of(sampleDeployment));
        when(deploymentRepository.findById(deploymentId)).thenReturn(Optional.of(sampleDeployment));

        doThrow(new RuntimeException("Git clone failed: authentication token expired"))
                .when(gitCloneService).cloneRepository(any(), any());

        assertThatThrownBy(() -> taskHandler.handleDeployment(sampleJob))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Git clone failed");

        verify(deploymentService).transitionStatus(deploymentId, DeploymentStatus.FAILED, "Git clone failed: authentication token expired");
        verify(buildLogService).storeLogs(eq(deploymentId), any());
        verify(workspaceManager).deleteWorkspace(deploymentId);
    }

    @Test
    @DisplayName("handleDeployment - Failure during Docker build marks deployment FAILED and stores logs")
    void handleDeployment_FailureInBuilding() {
        when(deploymentRepository.findByIdWithDetails(deploymentId)).thenReturn(Optional.of(sampleDeployment));
        when(deploymentRepository.findById(deploymentId)).thenReturn(Optional.of(sampleDeployment));

        doThrow(new RuntimeException("Docker build error: compilation failure"))
                .when(dockerBuildService).buildImage(any(), any(), any());

        assertThatThrownBy(() -> taskHandler.handleDeployment(sampleJob))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Docker build error");

        verify(deploymentService).transitionStatus(deploymentId, DeploymentStatus.FAILED, "Docker build error: compilation failure");
        verify(buildLogService).storeLogs(eq(deploymentId), any());
        verify(workspaceManager).deleteWorkspace(deploymentId);
    }

    @Test
    @DisplayName("handleDeployment - Failure during port allocation halts execution and marks deployment FAILED")
    void handleDeployment_FailureInPortAllocation() {
        when(deploymentRepository.findByIdWithDetails(deploymentId)).thenReturn(Optional.of(sampleDeployment));
        when(deploymentRepository.findById(deploymentId)).thenReturn(Optional.of(sampleDeployment));

        doThrow(new ContainerException("No available ports in range", "PORT_EXHAUSTION"))
                .when(portAllocator).allocatePort();

        assertThatThrownBy(() -> taskHandler.handleDeployment(sampleJob))
                .isInstanceOf(ContainerException.class)
                .hasMessageContaining("No available ports");

        verify(deploymentService).transitionStatus(deploymentId, DeploymentStatus.FAILED, "No available ports in range");
        verify(dockerContainerService, never()).runContainer(any(), any());
        verify(containerHealthChecker, never()).verifyHealth(any(), anyInt(), any(), any());
    }

    @Test
    @DisplayName("handleDeployment - Failure during container execution rolls back allocated port and marks FAILED")
    void handleDeployment_FailureInContainerRun() {
        when(deploymentRepository.findByIdWithDetails(deploymentId)).thenReturn(Optional.of(sampleDeployment));
        when(deploymentRepository.findById(deploymentId)).thenReturn(Optional.of(sampleDeployment));

        doThrow(new ContainerException("Docker run failed: port already in use", "CONTAINER_RUN_FAILED"))
                .when(dockerContainerService).runContainer(any(), any());

        assertThatThrownBy(() -> taskHandler.handleDeployment(sampleJob))
                .isInstanceOf(ContainerException.class)
                .hasMessageContaining("Docker run failed");

        // Rollback port
        verify(portAllocator).releasePort(10050);
        verify(deploymentService).transitionStatus(deploymentId, DeploymentStatus.FAILED, "Docker run failed: port already in use");
        verify(containerHealthChecker, never()).verifyHealth(any(), anyInt(), any(), any());
    }

    @Test
    @DisplayName("handleDeployment - Failure during health check rolls back container and port and marks FAILED")
    void handleDeployment_FailureInHealthCheck() {
        when(deploymentRepository.findByIdWithDetails(deploymentId)).thenReturn(Optional.of(sampleDeployment));
        when(deploymentRepository.findById(deploymentId)).thenReturn(Optional.of(sampleDeployment));

        doThrow(new ContainerException("Container crashed on startup", "CONTAINER_CRASHED"))
                .when(containerHealthChecker).verifyHealth(any(), anyInt(), any(), any());

        assertThatThrownBy(() -> taskHandler.handleDeployment(sampleJob))
                .isInstanceOf(ContainerException.class)
                .hasMessageContaining("Container crashed");

        // Rollback container and port
        verify(dockerContainerService).removeContainer("c1234567890abcdef", true);
        verify(portAllocator).releasePort(10050);
        verify(deploymentService).transitionStatus(deploymentId, DeploymentStatus.FAILED, "Container crashed on startup");
    }

    @Test
    @DisplayName("handleDeployment - Successfully cleans up previous running container for same project upon success")
    void handleDeployment_CleansUpPreviousContainersOnSuccess() {
        when(deploymentRepository.findByIdWithDetails(deploymentId)).thenReturn(Optional.of(sampleDeployment));
        when(deploymentRepository.findById(deploymentId)).thenReturn(Optional.of(sampleDeployment));

        UUID oldDepId = UUID.randomUUID();
        Deployment oldDeployment = Deployment.builder()
                .id(oldDepId)
                .project(sampleProject)
                .containerId("old-container-xyz")
                .containerPort(10049)
                .status(DeploymentStatus.SUCCESS)
                .build();

        when(deploymentRepository.findByProjectIdAndContainerIdIsNotNullAndIdNot(sampleProject.getId(), deploymentId))
                .thenReturn(List.of(oldDeployment));

        taskHandler.handleDeployment(sampleJob);

        // Previous container stopped and removed
        verify(dockerContainerService).stopContainer("old-container-xyz", 10);
        verify(dockerContainerService).removeContainer("old-container-xyz", true);
        verify(portAllocator).releasePort(10049);

        // Old deployment container metadata cleared
        assertThat(oldDeployment.getContainerId()).isNull();
        assertThat(oldDeployment.getContainerPort()).isNull();
        verify(deploymentRepository).save(oldDeployment);

        // New deployment succeeded
        verify(deploymentService).transitionStatus(deploymentId, DeploymentStatus.SUCCESS, null);
    }

    @Test
    @DisplayName("handleDeployment - Broadcasts status changes to DeploymentLogService across pipeline stages")
    void handleDeployment_BroadcastsStatusChanges() throws Exception {
        when(deploymentRepository.findByIdWithDetails(deploymentId)).thenReturn(Optional.of(sampleDeployment));
        when(deploymentRepository.findById(deploymentId)).thenReturn(Optional.of(sampleDeployment));

        taskHandler.handleDeployment(sampleJob);

        verify(deploymentLogService).recordStatusChange(eq(deploymentId), eq(DeploymentStatus.CLONING), anyString());
        verify(deploymentLogService).recordStatusChange(eq(deploymentId), eq(DeploymentStatus.DETECTING), anyString());
        verify(deploymentLogService).recordStatusChange(eq(deploymentId), eq(DeploymentStatus.BUILDING), anyString());
        verify(deploymentLogService).recordStatusChange(eq(deploymentId), eq(DeploymentStatus.STARTING), anyString());
        verify(deploymentLogService).recordStatusChange(eq(deploymentId), eq(DeploymentStatus.HEALTH_CHECKING), anyString());
        verify(deploymentLogService).recordStatusChange(eq(deploymentId), eq(DeploymentStatus.SUCCESS), anyString());
    }
}

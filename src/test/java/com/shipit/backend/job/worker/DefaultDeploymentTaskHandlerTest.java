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
import com.shipit.backend.deployment.entity.Deployment;
import com.shipit.backend.deployment.entity.DeploymentStatus;
import com.shipit.backend.deployment.repository.DeploymentRepository;
import com.shipit.backend.deployment.service.DeploymentService;
import com.shipit.backend.job.entity.DeploymentJob;
import com.shipit.backend.job.service.DeploymentJobService;
import com.shipit.backend.project.entity.Project;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
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
    }

    @Test
    @DisplayName("handleDeployment - Successfully executes clone, detect, generate, build and lifecycle transitions to SUCCESS")
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

        // Verify image tag updated on deployment
        assertThat(sampleDeployment.getDockerImageTag()).isEqualTo("shipit/sample-app:abc1234");
        verify(deploymentRepository).save(sampleDeployment);

        // Verify cleanup in finally
        verify(buildLogService).storeLogs(eq(deploymentId), any());
        verify(workspaceManager).deleteWorkspace(deploymentId);

        // Verify heartbeat recorded at multiple stages
        verify(jobService, atLeast(5)).recordHeartbeat(jobId);
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
}

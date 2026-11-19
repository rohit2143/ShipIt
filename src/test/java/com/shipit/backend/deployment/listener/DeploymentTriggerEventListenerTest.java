package com.shipit.backend.deployment.listener;

import com.shipit.backend.deployment.entity.Deployment;
import com.shipit.backend.deployment.entity.DeploymentStatus;
import com.shipit.backend.deployment.entity.DeploymentTrigger;
import com.shipit.backend.deployment.repository.DeploymentRepository;
import com.shipit.backend.job.service.DeploymentJobService;
import com.shipit.backend.project.entity.Project;
import com.shipit.backend.project.entity.ProjectStatus;
import com.shipit.backend.project.event.ProjectDeploymentTriggerEvent;
import com.shipit.backend.project.repository.ProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeploymentTriggerEventListenerTest {

    @Mock
    private DeploymentRepository deploymentRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private DeploymentJobService deploymentJobService;

    @InjectMocks
    private DeploymentTriggerEventListener listener;

    private UUID projectId;
    private Project sampleProject;
    private ProjectDeploymentTriggerEvent sampleEvent;

    @BeforeEach
    void setUp() {
        projectId = UUID.randomUUID();

        sampleProject = Project.builder()
                .id(projectId)
                .name("Production Service")
                .deploymentBranch("main")
                .status(ProjectStatus.ACTIVE)
                .build();

        sampleEvent = ProjectDeploymentTriggerEvent.builder()
                .projectId(projectId)
                .projectName("Production Service")
                .repositoryId(UUID.randomUUID())
                .repoFullName("shipit/production-service")
                .branch("main")
                .commitSha("abcdef0123456789abcdef0123456789abcdef01")
                .commitMessage("feat: enhance deployment pipeline")
                .pusherName("Rohit Developer")
                .pusherEmail("dev@shipit.dev")
                .triggeredAt(Instant.now())
                .build();
    }

    @Test
    @DisplayName("Should do nothing when event is null")
    void onProjectDeploymentTrigger_NullEvent() {
        listener.onProjectDeploymentTrigger(null);

        verifyNoInteractions(projectRepository);
        verifyNoInteractions(deploymentRepository);
        verifyNoInteractions(deploymentJobService);
    }

    @Test
    @DisplayName("Should do nothing when event projectId is null")
    void onProjectDeploymentTrigger_NullProjectId() {
        ProjectDeploymentTriggerEvent event = ProjectDeploymentTriggerEvent.builder()
                .projectId(null)
                .branch("main")
                .build();

        listener.onProjectDeploymentTrigger(event);

        verifyNoInteractions(projectRepository);
        verifyNoInteractions(deploymentRepository);
        verifyNoInteractions(deploymentJobService);
    }

    @Test
    @DisplayName("Should do nothing when project is not found in database")
    void onProjectDeploymentTrigger_ProjectNotFound() {
        when(projectRepository.findById(projectId)).thenReturn(Optional.empty());

        listener.onProjectDeploymentTrigger(sampleEvent);

        verify(projectRepository).findById(projectId);
        verifyNoInteractions(deploymentRepository);
        verifyNoInteractions(deploymentJobService);
    }

    @Test
    @DisplayName("Should do nothing when project is not ACTIVE")
    void onProjectDeploymentTrigger_ProjectNotActive() {
        sampleProject.setStatus(ProjectStatus.ARCHIVED);
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(sampleProject));

        listener.onProjectDeploymentTrigger(sampleEvent);

        verify(projectRepository).findById(projectId);
        verifyNoInteractions(deploymentRepository);
        verifyNoInteractions(deploymentJobService);
    }

    @Test
    @DisplayName("Should successfully queue deployment and background job on valid event")
    void onProjectDeploymentTrigger_Success() {
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(sampleProject));
        when(deploymentRepository.save(any(Deployment.class))).thenAnswer(inv -> {
            Deployment d = inv.getArgument(0);
            d.setId(UUID.randomUUID());
            return d;
        });

        listener.onProjectDeploymentTrigger(sampleEvent);

        ArgumentCaptor<Deployment> captor = ArgumentCaptor.forClass(Deployment.class);
        verify(deploymentRepository).save(captor.capture());

        Deployment saved = captor.getValue();
        assertThat(saved.getProject()).isEqualTo(sampleProject);
        assertThat(saved.getStatus()).isEqualTo(DeploymentStatus.QUEUED);
        assertThat(saved.getTrigger()).isEqualTo(DeploymentTrigger.WEBHOOK);
        assertThat(saved.getCommitBranch()).isEqualTo("main");
        assertThat(saved.getCommitSha()).isEqualTo("abcdef0123456789abcdef0123456789abcdef01");
        assertThat(saved.getCommitMessage()).isEqualTo("feat: enhance deployment pipeline");
        assertThat(saved.getCommitAuthor()).isEqualTo("Rohit Developer");
        assertThat(saved.getQueuedAt()).isEqualTo(sampleEvent.getTriggeredAt());

        verify(deploymentJobService).createJob(eq(saved), eq(0));
    }

    @Test
    @DisplayName("Should truncate long commit message, fallback author, and enqueue job")
    void onProjectDeploymentTrigger_TruncationAndFallback() {
        String longMessage = "Commit Message: ".repeat(100); // > 1000 chars
        ProjectDeploymentTriggerEvent event = ProjectDeploymentTriggerEvent.builder()
                .projectId(projectId)
                .branch(null) // null branch should fallback to project's default branch
                .commitSha("abcdef0123456789abcdef0123456789abcdef0123456789") // > 40 chars
                .commitMessage(longMessage)
                .pusherName("")
                .pusherEmail("onlyemail@shipit.dev")
                .triggeredAt(null)
                .build();

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(sampleProject));
        when(deploymentRepository.save(any(Deployment.class))).thenAnswer(inv -> inv.getArgument(0));

        listener.onProjectDeploymentTrigger(event);

        ArgumentCaptor<Deployment> captor = ArgumentCaptor.forClass(Deployment.class);
        verify(deploymentRepository).save(captor.capture());

        Deployment saved = captor.getValue();
        assertThat(saved.getCommitBranch()).isEqualTo("main"); // fell back to sampleProject.getDeploymentBranch()
        assertThat(saved.getCommitMessage()).hasSize(1000);
        assertThat(saved.getCommitSha()).hasSize(40);
        assertThat(saved.getCommitAuthor()).isEqualTo("onlyemail@shipit.dev");
        assertThat(saved.getQueuedAt()).isNotNull();

        verify(deploymentJobService).createJob(eq(saved), eq(0));
    }
}

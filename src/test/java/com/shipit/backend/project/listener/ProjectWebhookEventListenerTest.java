package com.shipit.backend.project.listener;

import com.shipit.backend.github.event.GitHubPushReceivedEvent;
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
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProjectWebhookEventListenerTest {

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private ProjectWebhookEventListener listener;

    private UUID repoId;
    private GitHubPushReceivedEvent validEvent;

    @BeforeEach
    void setUp() {
        repoId = UUID.randomUUID();
        validEvent = GitHubPushReceivedEvent.builder()
                .repositoryId(repoId)
                .githubRepoId(99999L)
                .repoFullName("rohit/monorepo-service")
                .cloneUrl("https://github.com/rohit/monorepo-service.git")
                .defaultBranch("main")
                .branch("main")
                .commitSha("abc123def456")
                .commitMessage("feat: add core engine")
                .pusherName("Rohit Kumar")
                .pusherEmail("rohit@example.com")
                .timestamp(Instant.now())
                .build();
    }

    @Test
    @DisplayName("Should do nothing when event is null")
    void shouldDoNothingWhenEventIsNull() {
        listener.onGitHubPushReceived(null);

        verifyNoInteractions(projectRepository);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("Should do nothing when repositoryId is null")
    void shouldDoNothingWhenRepositoryIdIsNull() {
        GitHubPushReceivedEvent event = GitHubPushReceivedEvent.builder()
                .repositoryId(null)
                .branch("main")
                .build();

        listener.onGitHubPushReceived(event);

        verifyNoInteractions(projectRepository);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("Should do nothing when branch is null")
    void shouldDoNothingWhenBranchIsNull() {
        GitHubPushReceivedEvent event = GitHubPushReceivedEvent.builder()
                .repositoryId(repoId)
                .branch(null)
                .build();

        listener.onGitHubPushReceived(event);

        verifyNoInteractions(projectRepository);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("Should query eligible projects and publish nothing when no projects match")
    void shouldPublishNothingWhenNoEligibleProjectsFound() {
        when(projectRepository.findActiveAutoDeployProjects(repoId, "main", ProjectStatus.ACTIVE))
                .thenReturn(Collections.emptyList());

        listener.onGitHubPushReceived(validEvent);

        verify(projectRepository).findActiveAutoDeployProjects(repoId, "main", ProjectStatus.ACTIVE);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("Should dispatch ProjectDeploymentTriggerEvent for a single matched project")
    void shouldDispatchEventForSingleProject() {
        UUID projectId = UUID.randomUUID();
        Project project = Project.builder()
                .id(projectId)
                .name("Backend API")
                .deploymentBranch("main")
                .autoDeploy(true)
                .status(ProjectStatus.ACTIVE)
                .build();

        when(projectRepository.findActiveAutoDeployProjects(repoId, "main", ProjectStatus.ACTIVE))
                .thenReturn(List.of(project));

        listener.onGitHubPushReceived(validEvent);

        ArgumentCaptor<ProjectDeploymentTriggerEvent> captor =
                ArgumentCaptor.forClass(ProjectDeploymentTriggerEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());

        ProjectDeploymentTriggerEvent triggered = captor.getValue();
        assertThat(triggered).isNotNull();
        assertThat(triggered.getProjectId()).isEqualTo(projectId);
        assertThat(triggered.getProjectName()).isEqualTo("Backend API");
        assertThat(triggered.getRepositoryId()).isEqualTo(repoId);
        assertThat(triggered.getRepoFullName()).isEqualTo("rohit/monorepo-service");
        assertThat(triggered.getBranch()).isEqualTo("main");
        assertThat(triggered.getCommitSha()).isEqualTo("abc123def456");
        assertThat(triggered.getCommitMessage()).isEqualTo("feat: add core engine");
        assertThat(triggered.getPusherName()).isEqualTo("Rohit Kumar");
        assertThat(triggered.getPusherEmail()).isEqualTo("rohit@example.com");
        assertThat(triggered.getTriggeredAt()).isNotNull();
    }

    @Test
    @DisplayName("Should dispatch multiple ProjectDeploymentTriggerEvents when multiple projects match repository and branch")
    void shouldDispatchEventsForMultipleProjects() {
        UUID project1Id = UUID.randomUUID();
        Project project1 = Project.builder()
                .id(project1Id)
                .name("Backend Service")
                .deploymentBranch("main")
                .autoDeploy(true)
                .status(ProjectStatus.ACTIVE)
                .build();

        UUID project2Id = UUID.randomUUID();
        Project project2 = Project.builder()
                .id(project2Id)
                .name("Frontend Service")
                .deploymentBranch("main")
                .autoDeploy(true)
                .status(ProjectStatus.ACTIVE)
                .build();

        when(projectRepository.findActiveAutoDeployProjects(repoId, "main", ProjectStatus.ACTIVE))
                .thenReturn(List.of(project1, project2));

        listener.onGitHubPushReceived(validEvent);

        ArgumentCaptor<ProjectDeploymentTriggerEvent> captor =
                ArgumentCaptor.forClass(ProjectDeploymentTriggerEvent.class);
        verify(eventPublisher, times(2)).publishEvent(captor.capture());

        List<ProjectDeploymentTriggerEvent> events = captor.getAllValues();
        assertThat(events).hasSize(2);

        assertThat(events.get(0).getProjectId()).isEqualTo(project1Id);
        assertThat(events.get(0).getProjectName()).isEqualTo("Backend Service");
        assertThat(events.get(0).getCommitSha()).isEqualTo("abc123def456");

        assertThat(events.get(1).getProjectId()).isEqualTo(project2Id);
        assertThat(events.get(1).getProjectName()).isEqualTo("Frontend Service");
        assertThat(events.get(1).getCommitSha()).isEqualTo("abc123def456");
    }
}

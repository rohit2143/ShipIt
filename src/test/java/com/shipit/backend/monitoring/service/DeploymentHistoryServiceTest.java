package com.shipit.backend.monitoring.service;

import com.shipit.backend.common.exception.ResourceNotFoundException;
import com.shipit.backend.deployment.entity.Deployment;
import com.shipit.backend.deployment.entity.DeploymentStatus;
import com.shipit.backend.deployment.repository.DeploymentRepository;
import com.shipit.backend.monitoring.dto.DeploymentTimelineEntry;
import com.shipit.backend.monitoring.dto.ProjectDeploymentHistoryResponse;
import com.shipit.backend.project.entity.Project;
import com.shipit.backend.project.repository.ProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeploymentHistoryServiceTest {

    @Mock
    private DeploymentRepository deploymentRepository;

    @Mock
    private ProjectRepository projectRepository;

    @InjectMocks
    private DeploymentHistoryService historyService;

    private UUID userId;
    private UUID projectId;
    private Project sampleProject;
    private Deployment dep1;
    private Deployment dep2;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        projectId = UUID.randomUUID();

        sampleProject = Project.builder()
                .id(projectId)
                .name("ShipIt Demo App")
                .build();

        Instant t0 = Instant.parse("2026-11-20T10:00:00Z");
        Instant t1 = Instant.parse("2026-11-20T10:01:00Z"); // 60s
        Instant t2 = Instant.parse("2026-11-20T11:00:00Z");
        Instant t3 = Instant.parse("2026-11-20T11:00:40Z"); // 40s

        dep1 = Deployment.builder()
                .id(UUID.randomUUID())
                .project(sampleProject)
                .status(DeploymentStatus.SUCCESS)
                .commitBranch("main")
                .commitSha("commit111")
                .startedAt(t0)
                .finishedAt(t1)
                .build();

        dep2 = Deployment.builder()
                .id(UUID.randomUUID())
                .project(sampleProject)
                .status(DeploymentStatus.FAILED)
                .commitBranch("main")
                .commitSha("commit222")
                .startedAt(t2)
                .finishedAt(t3)
                .errorMessage("Build failed")
                .build();
    }

    @Test
    @DisplayName("getProjectDeploymentHistory - Successfully compiles metrics and rollout timeline")
    void getProjectDeploymentHistory_Success() {
        when(projectRepository.findByIdAndUserId(projectId, userId)).thenReturn(Optional.of(sampleProject));

        when(deploymentRepository.findAllByProjectId(eq(projectId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(dep2, dep1)));

        when(deploymentRepository.countByProjectId(projectId)).thenReturn(2L);
        when(deploymentRepository.countByProjectIdAndStatus(projectId, DeploymentStatus.SUCCESS)).thenReturn(1L);
        when(deploymentRepository.countByProjectIdAndStatus(projectId, DeploymentStatus.FAILED)).thenReturn(1L);
        when(deploymentRepository.countByProjectIdAndStatus(projectId, DeploymentStatus.CANCELLED)).thenReturn(0L);

        when(deploymentRepository.findAllByProjectId(projectId)).thenReturn(List.of(dep1, dep2));
        when(deploymentRepository.findTopByProjectIdAndStatusOrderByCreatedAtDesc(projectId, DeploymentStatus.SUCCESS))
                .thenReturn(Optional.of(dep1));

        ProjectDeploymentHistoryResponse response = historyService.getProjectDeploymentHistory(userId, projectId, 0, 20);

        assertThat(response.getProjectId()).isEqualTo(projectId);
        assertThat(response.getProjectName()).isEqualTo("ShipIt Demo App");
        assertThat(response.getTotalDeployments()).isEqualTo(2L);
        assertThat(response.getSuccessfulDeployments()).isEqualTo(1L);
        assertThat(response.getFailedDeployments()).isEqualTo(1L);
        assertThat(response.getCancelledDeployments()).isEqualTo(0L);
        assertThat(response.getSuccessRatePercent()).isEqualTo(50.0);
        assertThat(response.getAverageDurationSeconds()).isEqualTo(50.0); // (60 + 40) / 2 = 50.0
        assertThat(response.getActiveDeployment()).isNotNull();
        assertThat(response.getActiveDeployment().getDeploymentId()).isEqualTo(dep1.getId());
        assertThat(response.getTimeline()).hasSize(2);
        assertThat(response.isHasMore()).isFalse();
    }

    @Test
    @DisplayName("getProjectDeploymentHistory - Throws ResourceNotFoundException when project not found")
    void getProjectDeploymentHistory_NotFound() {
        when(projectRepository.findByIdAndUserId(projectId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> historyService.getProjectDeploymentHistory(userId, projectId, 0, 20))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("getDeploymentTimelineDetails - Successfully retrieves single deployment details")
    void getDeploymentTimelineDetails_Success() {
        when(deploymentRepository.findByIdAndUserId(dep1.getId(), userId)).thenReturn(Optional.of(dep1));

        DeploymentTimelineEntry details = historyService.getDeploymentTimelineDetails(userId, dep1.getId());

        assertThat(details.getDeploymentId()).isEqualTo(dep1.getId());
        assertThat(details.getCommitSha()).isEqualTo("commit111");
        assertThat(details.getStatus()).isEqualTo(DeploymentStatus.SUCCESS);
        assertThat(details.getDurationSeconds()).isEqualTo(60L);
    }

    @Test
    @DisplayName("getDeploymentTimelineDetails - Throws ResourceNotFoundException when deployment not found")
    void getDeploymentTimelineDetails_NotFound() {
        UUID randomId = UUID.randomUUID();
        when(deploymentRepository.findByIdAndUserId(randomId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> historyService.getDeploymentTimelineDetails(userId, randomId))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}

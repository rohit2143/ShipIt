package com.shipit.backend.monitoring.service;

import com.shipit.backend.common.exception.ResourceNotFoundException;
import com.shipit.backend.deployment.entity.Deployment;
import com.shipit.backend.deployment.entity.DeploymentStatus;
import com.shipit.backend.deployment.repository.DeploymentRepository;
import com.shipit.backend.monitoring.dto.DeploymentTimelineEntry;
import com.shipit.backend.monitoring.dto.ProjectDeploymentHistoryResponse;
import com.shipit.backend.project.entity.Project;
import com.shipit.backend.project.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service that calculates deployment metrics, success rates, durations,
 * and compiles historical rollout timelines for projects.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeploymentHistoryService {

    private final DeploymentRepository deploymentRepository;
    private final ProjectRepository projectRepository;

    /**
     * Retrieves aggregated deployment statistics and a paginated rollout timeline for a project.
     *
     * @param userId Authenticated user requesting history.
     * @param projectId Target project ID.
     * @param page Zero-based page index.
     * @param size Page size (clamped 1..100).
     * @return ProjectDeploymentHistoryResponse containing metrics and timeline.
     */
    @Transactional(readOnly = true)
    public ProjectDeploymentHistoryResponse getProjectDeploymentHistory(
            UUID userId,
            UUID projectId,
            int page,
            int size
    ) {
        if (userId == null || projectId == null) {
            throw new IllegalArgumentException("userId and projectId must not be null");
        }

        Project project = projectRepository.findByIdAndUserId(projectId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found with id: " + projectId));

        int safePage = Math.max(0, page);
        int safeSize = (size <= 0 || size > 100) ? 20 : size;
        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<Deployment> deploymentPage = deploymentRepository.findAllByProjectId(projectId, pageable);

        long totalDeployments = deploymentRepository.countByProjectId(projectId);
        long successfulDeployments = deploymentRepository.countByProjectIdAndStatus(projectId, DeploymentStatus.SUCCESS);
        long failedDeployments = deploymentRepository.countByProjectIdAndStatus(projectId, DeploymentStatus.FAILED);
        long cancelledDeployments = deploymentRepository.countByProjectIdAndStatus(projectId, DeploymentStatus.CANCELLED);

        double successRate = totalDeployments > 0
                ? Math.round((successfulDeployments * 100.0 / totalDeployments) * 10.0) / 10.0
                : 0.0;

        List<Deployment> allDeployments = deploymentRepository.findAllByProjectId(projectId);
        double averageDuration = allDeployments.stream()
                .filter(d -> d.getStartedAt() != null && d.getFinishedAt() != null)
                .mapToLong(d -> Math.max(0L, Duration.between(d.getStartedAt(), d.getFinishedAt()).toSeconds()))
                .average()
                .orElse(0.0);
        averageDuration = Math.round(averageDuration * 10.0) / 10.0;

        Optional<Deployment> activeOpt = deploymentRepository
                .findTopByProjectIdAndStatusOrderByCreatedAtDesc(projectId, DeploymentStatus.SUCCESS);
        DeploymentTimelineEntry activeEntry = activeOpt.map(DeploymentTimelineEntry::fromEntity).orElse(null);

        List<DeploymentTimelineEntry> timeline = deploymentPage.getContent().stream()
                .map(DeploymentTimelineEntry::fromEntity)
                .toList();

        return ProjectDeploymentHistoryResponse.builder()
                .projectId(projectId)
                .projectName(project.getName())
                .totalDeployments(totalDeployments)
                .successfulDeployments(successfulDeployments)
                .failedDeployments(failedDeployments)
                .cancelledDeployments(cancelledDeployments)
                .successRatePercent(successRate)
                .averageDurationSeconds(averageDuration)
                .activeDeployment(activeEntry)
                .timeline(timeline)
                .page(safePage)
                .size(safeSize)
                .hasMore(deploymentPage.hasNext())
                .build();
    }

    /**
     * Retrieves timeline execution details for an individual deployment.
     */
    @Transactional(readOnly = true)
    public DeploymentTimelineEntry getDeploymentTimelineDetails(UUID userId, UUID deploymentId) {
        if (userId == null || deploymentId == null) {
            throw new IllegalArgumentException("userId and deploymentId must not be null");
        }

        Deployment deployment = deploymentRepository.findByIdAndUserId(deploymentId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Deployment not found with id: " + deploymentId));

        return DeploymentTimelineEntry.fromEntity(deployment);
    }
}

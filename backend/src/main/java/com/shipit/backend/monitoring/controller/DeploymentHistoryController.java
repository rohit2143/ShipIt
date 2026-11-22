package com.shipit.backend.monitoring.controller;

import com.shipit.backend.auth.security.UserPrincipal;
import com.shipit.backend.common.response.ApiResponse;
import com.shipit.backend.monitoring.dto.DeploymentTimelineEntry;
import com.shipit.backend.monitoring.dto.ProjectDeploymentHistoryResponse;
import com.shipit.backend.monitoring.service.DeploymentHistoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Controller exposing project deployment history, rollout frequency, success rate metrics,
 * and individual deployment execution timelines.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class DeploymentHistoryController {

    private final DeploymentHistoryService historyService;

    /**
     * Retrieves aggregated deployment statistics, success rate, average duration,
     * and a paginated rollout timeline for a project.
     */
    @GetMapping("/projects/{projectId}/deployment-history")
    public ResponseEntity<ApiResponse<ProjectDeploymentHistoryResponse>> getProjectHistory(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable UUID projectId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        log.debug("User {} queried deployment history for project {} (page={}, size={})",
                currentUser.getId(), projectId, page, size);

        ProjectDeploymentHistoryResponse response = historyService.getProjectDeploymentHistory(
                currentUser.getId(), projectId, page, size
        );
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Retrieves detailed execution timeline and duration metrics for a specific deployment.
     */
    @GetMapping("/deployments/{id}/timeline")
    public ResponseEntity<ApiResponse<DeploymentTimelineEntry>> getDeploymentTimeline(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable UUID id
    ) {
        log.debug("User {} queried timeline details for deployment {}", currentUser.getId(), id);

        DeploymentTimelineEntry entry = historyService.getDeploymentTimelineDetails(currentUser.getId(), id);
        return ResponseEntity.ok(ApiResponse.success(entry));
    }
}

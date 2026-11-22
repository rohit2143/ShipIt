package com.shipit.backend.monitoring.controller;

import com.shipit.backend.auth.security.UserPrincipal;
import com.shipit.backend.common.exception.ResourceNotFoundException;
import com.shipit.backend.common.response.ApiResponse;
import com.shipit.backend.deployment.repository.DeploymentRepository;
import com.shipit.backend.monitoring.model.DeploymentHealthReport;
import com.shipit.backend.monitoring.service.DeploymentHealthMonitor;
import com.shipit.backend.project.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Controller exposing real-time container health probes, availability checks,
 * and hardware resource metrics for deployments and projects.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class DeploymentHealthController {

    private final DeploymentHealthMonitor healthMonitor;
    private final DeploymentRepository deploymentRepository;
    private final ProjectRepository projectRepository;

    /**
     * Inspects active health, HTTP probe status, and hardware metrics for a specific deployment.
     */
    @GetMapping("/deployments/{id}/health")
    public ResponseEntity<ApiResponse<DeploymentHealthReport>> getDeploymentHealth(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable UUID id
    ) {
        log.debug("User {} requested health inspection for deployment {}", currentUser.getId(), id);

        deploymentRepository.findByIdAndUserId(id, currentUser.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Deployment not found with id: " + id));

        DeploymentHealthReport report = healthMonitor.checkDeploymentHealth(id);
        return ResponseEntity.ok(ApiResponse.success(report));
    }

    /**
     * Retrieves health status and metrics of the current active live deployment for a project.
     */
    @GetMapping("/projects/{projectId}/health")
    public ResponseEntity<ApiResponse<DeploymentHealthReport>> getProjectActiveHealth(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable UUID projectId
    ) {
        log.debug("User {} requested active health for project {}", currentUser.getId(), projectId);

        projectRepository.findByIdAndUserId(projectId, currentUser.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Project not found with id: " + projectId));

        DeploymentHealthReport report = healthMonitor.getProjectActiveHealth(projectId);
        return ResponseEntity.ok(ApiResponse.success(report));
    }
}

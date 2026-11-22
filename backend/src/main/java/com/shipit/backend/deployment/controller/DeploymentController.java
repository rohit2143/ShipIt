package com.shipit.backend.deployment.controller;

import com.shipit.backend.auth.security.UserPrincipal;
import com.shipit.backend.common.response.ApiResponse;
import com.shipit.backend.common.response.PageResponse;
import com.shipit.backend.deployment.dto.DeploymentResponse;
import com.shipit.backend.deployment.dto.DeploymentSummaryResponse;
import com.shipit.backend.deployment.dto.TriggerDeploymentRequest;
import com.shipit.backend.deployment.service.DeploymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RestController
@RequiredArgsConstructor
public class DeploymentController {

    private final DeploymentService deploymentService;

    @PostMapping("/api/v1/projects/{projectId}/deployments")
    public ResponseEntity<ApiResponse<DeploymentResponse>> triggerDeployment(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable UUID projectId,
            @Valid @RequestBody(required = false) TriggerDeploymentRequest request
    ) {
        log.info("Received request to trigger deployment for project id: {} by user: {}", projectId, currentUser.getId());
        DeploymentResponse response = deploymentService.triggerManualDeployment(currentUser.getId(), projectId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Deployment triggered successfully", response));
    }

    @GetMapping("/api/v1/projects/{projectId}/deployments")
    public ResponseEntity<ApiResponse<PageResponse<DeploymentSummaryResponse>>> getProjectDeployments(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable UUID projectId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        log.debug("Received request to list deployments for project id: {} by user: {}", projectId, currentUser.getId());
        Page<DeploymentSummaryResponse> page = deploymentService.getProjectDeployments(currentUser.getId(), projectId, pageable);
        return ResponseEntity.ok(ApiResponse.success(PageResponse.fromPage(page)));
    }

    @GetMapping("/api/v1/deployments/{id}")
    public ResponseEntity<ApiResponse<DeploymentResponse>> getDeployment(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable UUID id
    ) {
        log.debug("Received request to get deployment id: {} by user: {}", id, currentUser.getId());
        DeploymentResponse response = deploymentService.getDeployment(currentUser.getId(), id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/api/v1/deployments/{id}/cancel")
    public ResponseEntity<ApiResponse<DeploymentResponse>> cancelDeployment(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable UUID id
    ) {
        log.info("Received request to cancel deployment id: {} by user: {}", id, currentUser.getId());
        DeploymentResponse response = deploymentService.cancelDeployment(currentUser.getId(), id);
        return ResponseEntity.ok(ApiResponse.success("Deployment cancelled successfully", response));
    }

    @PostMapping("/api/v1/deployments/{id}/retry")
    public ResponseEntity<ApiResponse<DeploymentResponse>> retryDeployment(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable UUID id
    ) {
        log.info("Received request to retry deployment id: {} by user: {}", id, currentUser.getId());
        DeploymentResponse response = deploymentService.retryDeployment(currentUser.getId(), id);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Deployment retried successfully", response));
    }
}

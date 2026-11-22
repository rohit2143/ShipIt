package com.shipit.backend.job.controller;

import com.shipit.backend.auth.security.UserPrincipal;
import com.shipit.backend.common.response.ApiResponse;
import com.shipit.backend.job.dto.JobQueueStatsResponse;
import com.shipit.backend.job.dto.JobResponse;
import com.shipit.backend.job.service.DeploymentJobService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequiredArgsConstructor
public class DeploymentJobController {

    private final DeploymentJobService jobService;

    /**
     * Retrieves background job details by job ID.
     * Verifies that the job belongs to a project owned by the authenticated user.
     */
    @GetMapping("/api/v1/jobs/{id}")
    public ResponseEntity<ApiResponse<JobResponse>> getJob(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable UUID id
    ) {
        log.debug("User {} requested job details for id: {}", currentUser.getId(), id);
        JobResponse response = jobService.getJobForUser(currentUser.getId(), id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Retrieves the latest background job for the specified deployment.
     * Verifies that the deployment belongs to a project owned by the authenticated user.
     */
    @GetMapping("/api/v1/deployments/{deploymentId}/job")
    public ResponseEntity<ApiResponse<JobResponse>> getLatestJobForDeployment(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable UUID deploymentId
    ) {
        log.debug("User {} requested latest job for deployment id: {}", currentUser.getId(), deploymentId);
        JobResponse response = jobService.getLatestJobForDeploymentAndUser(currentUser.getId(), deploymentId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Retrieves all background jobs associated with the specified deployment (ordered newest first).
     * Verifies that the deployment belongs to a project owned by the authenticated user.
     */
    @GetMapping("/api/v1/deployments/{deploymentId}/jobs")
    public ResponseEntity<ApiResponse<List<JobResponse>>> getAllJobsForDeployment(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable UUID deploymentId
    ) {
        log.debug("User {} requested all jobs for deployment id: {}", currentUser.getId(), deploymentId);
        List<JobResponse> responses = jobService.getAllJobsForDeploymentAndUser(currentUser.getId(), deploymentId);
        return ResponseEntity.ok(ApiResponse.success(responses));
    }

    /**
     * Retrieves high-level job queue metrics and counters across all job statuses.
     */
    @GetMapping("/api/v1/jobs/stats")
    public ResponseEntity<ApiResponse<JobQueueStatsResponse>> getQueueStats(
            @AuthenticationPrincipal UserPrincipal currentUser
    ) {
        log.debug("User {} requested job queue statistics", currentUser.getId());
        JobQueueStatsResponse response = jobService.getQueueStats();
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}

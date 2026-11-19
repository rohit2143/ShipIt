package com.shipit.backend.build.controller;

import com.shipit.backend.auth.security.UserPrincipal;
import com.shipit.backend.build.dto.BuildLogsResponse;
import com.shipit.backend.build.service.BuildLogService;
import com.shipit.backend.common.response.ApiResponse;
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
 * Controller exposing endpoints for retrieving deployment build execution logs.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/deployments")
public class DeploymentBuildLogsController {

    private final BuildLogService buildLogService;

    @GetMapping("/{id}/logs")
    public ResponseEntity<ApiResponse<BuildLogsResponse>> getBuildLogs(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable UUID id
    ) {
        log.debug("Received request to get build logs for deployment id: {} by user: {}", id, currentUser.getId());
        BuildLogsResponse response = buildLogService.getLogs(currentUser.getId(), id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}

package com.shipit.backend.log.controller;

import com.shipit.backend.auth.security.UserPrincipal;
import com.shipit.backend.common.response.ApiResponse;
import com.shipit.backend.log.dto.DeploymentLogsPageResponse;
import com.shipit.backend.log.entity.DeploymentStage;
import com.shipit.backend.log.service.DeploymentLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

/**
 * Controller exposing real-time SSE log streaming, paginated historical querying,
 * stage-based filtering, and plain-text log downloads for deployments.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/deployments")
public class DeploymentLogController {

    private final DeploymentLogService deploymentLogService;

    /**
     * Subscribes to the live Server-Sent Events (SSE) log stream for a deployment.
     */
    @GetMapping(value = "/{id}/logs/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamDeploymentLogs(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable UUID id,
            @RequestParam(required = false) Long since
    ) {
        log.debug("User {} subscribed to live log stream for deployment {}", currentUser.getId(), id);
        return deploymentLogService.subscribeToStream(currentUser.getId(), id, since);
    }

    /**
     * Retrieves paginated, structured historical log records with optional stage and cursor filtering.
     */
    @GetMapping({"/{id}/logs/history", "/{id}/logs/events"})
    public ResponseEntity<ApiResponse<DeploymentLogsPageResponse>> getHistoricalLogs(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable UUID id,
            @RequestParam(required = false) DeploymentStage stage,
            @RequestParam(required = false) Long since,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size
    ) {
        log.debug("User {} queried logs for deployment {} (stage={}, since={}, page={}, size={})",
                currentUser.getId(), id, stage, since, page, size);

        DeploymentLogsPageResponse response = deploymentLogService.getLogs(
                currentUser.getId(), id, stage, since, page, size
        );
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Downloads the complete plain-text execution log file for a deployment.
     */
    @GetMapping(value = "/{id}/logs/download", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> downloadFullLogs(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable UUID id
    ) {
        log.info("User {} requested full log download for deployment {}", currentUser.getId(), id);

        String fullLogs = deploymentLogService.getFullLogsAsString(currentUser.getId(), id);
        String filename = String.format("deployment-%s.log", id);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/plain; charset=UTF-8"))
                .body(fullLogs);
    }
}

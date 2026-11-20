package com.shipit.backend.container.controller;

import com.shipit.backend.auth.security.UserPrincipal;
import com.shipit.backend.common.response.ApiResponse;
import com.shipit.backend.container.dto.ContainerLogsResponse;
import com.shipit.backend.container.model.ContainerInfo;
import com.shipit.backend.container.service.ContainerManagementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/deployments")
public class DeploymentContainerController {

    private final ContainerManagementService containerManagementService;

    @GetMapping("/{id}/container")
    public ResponseEntity<ApiResponse<ContainerInfo>> inspectContainer(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable UUID id
    ) {
        log.debug("User {} requested inspection of container for deployment {}", currentUser.getId(), id);
        ContainerInfo info = containerManagementService.inspectDeploymentContainer(currentUser.getId(), id);
        return ResponseEntity.ok(ApiResponse.success(info));
    }

    @PostMapping({"/{id}/container/restart", "/{id}/restart"})
    public ResponseEntity<ApiResponse<ContainerInfo>> restartContainer(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable UUID id
    ) {
        log.info("User {} requested restart of container for deployment {}", currentUser.getId(), id);
        ContainerInfo info = containerManagementService.restartDeploymentContainer(currentUser.getId(), id);
        return ResponseEntity.ok(ApiResponse.success("Container restarted successfully", info));
    }

    @PostMapping({"/{id}/container/stop", "/{id}/stop"})
    public ResponseEntity<ApiResponse<ContainerInfo>> stopContainer(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable UUID id
    ) {
        log.info("User {} requested stop of container for deployment {}", currentUser.getId(), id);
        ContainerInfo info = containerManagementService.stopDeploymentContainer(currentUser.getId(), id);
        return ResponseEntity.ok(ApiResponse.success("Container stopped successfully", info));
    }

    @GetMapping("/{id}/container/logs")
    public ResponseEntity<ApiResponse<ContainerLogsResponse>> getContainerLogs(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable UUID id,
            @RequestParam(defaultValue = "100") int lines
    ) {
        log.debug("User {} requested {} lines of container logs for deployment {}", currentUser.getId(), lines, id);
        ContainerLogsResponse logs = containerManagementService.getDeploymentContainerLogs(currentUser.getId(), id, lines);
        return ResponseEntity.ok(ApiResponse.success(logs));
    }
}

package com.shipit.backend.project.controller;

import com.shipit.backend.auth.security.UserPrincipal;
import com.shipit.backend.common.response.ApiResponse;
import com.shipit.backend.project.dto.BatchEnvironmentVariablesRequest;
import com.shipit.backend.project.dto.EnvironmentVariableRequest;
import com.shipit.backend.project.dto.EnvironmentVariableResponse;
import com.shipit.backend.project.service.ProjectEnvironmentVariableService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/projects/{projectId}/env")
@RequiredArgsConstructor
public class ProjectEnvironmentVariableController {

    private final ProjectEnvironmentVariableService environmentVariableService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<EnvironmentVariableResponse>>> getEnvironmentVariables(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable UUID projectId
    ) {
        log.debug("Received request to list env vars for project: {} user: {}", projectId, currentUser.getId());
        List<EnvironmentVariableResponse> response =
                environmentVariableService.getProjectEnvironmentVariables(currentUser.getId(), projectId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{key}")
    public ResponseEntity<ApiResponse<EnvironmentVariableResponse>> getEnvironmentVariable(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable UUID projectId,
            @PathVariable String key
    ) {
        log.debug("Received request to get env var '{}' for project: {} user: {}", key, projectId, currentUser.getId());
        EnvironmentVariableResponse response =
                environmentVariableService.getProjectEnvironmentVariable(currentUser.getId(), projectId, key);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<EnvironmentVariableResponse>> upsertEnvironmentVariable(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable UUID projectId,
            @Valid @RequestBody EnvironmentVariableRequest request
    ) {
        log.info("Received request to upsert env var '{}' for project: {} user: {}",
                request.getKey(), projectId, currentUser.getId());
        EnvironmentVariableResponse response =
                environmentVariableService.upsertEnvironmentVariable(currentUser.getId(), projectId, request);
        return ResponseEntity.ok(ApiResponse.success("Environment variable saved successfully", response));
    }

    @RequestMapping(value = "/batch", method = {RequestMethod.POST, RequestMethod.PUT})
    public ResponseEntity<ApiResponse<List<EnvironmentVariableResponse>>> batchUpsertEnvironmentVariables(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable UUID projectId,
            @Valid @RequestBody BatchEnvironmentVariablesRequest request
    ) {
        log.info("Received request to batch upsert {} env vars for project: {} user: {}",
                request.getVariables().size(), projectId, currentUser.getId());
        List<EnvironmentVariableResponse> response =
                environmentVariableService.batchUpsertEnvironmentVariables(currentUser.getId(), projectId, request);
        return ResponseEntity.ok(ApiResponse.success("Environment variables saved successfully", response));
    }

    @DeleteMapping("/{key}")
    public ResponseEntity<ApiResponse<Void>> deleteEnvironmentVariable(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable UUID projectId,
            @PathVariable String key
    ) {
        log.info("Received request to delete env var '{}' for project: {} user: {}",
                key, projectId, currentUser.getId());
        environmentVariableService.deleteEnvironmentVariable(currentUser.getId(), projectId, key);
        return ResponseEntity.ok(ApiResponse.message("Environment variable deleted successfully"));
    }
}

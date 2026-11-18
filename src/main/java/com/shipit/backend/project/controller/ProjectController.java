package com.shipit.backend.project.controller;

import com.shipit.backend.auth.security.UserPrincipal;
import com.shipit.backend.common.response.ApiResponse;
import com.shipit.backend.project.dto.CreateProjectRequest;
import com.shipit.backend.project.dto.ProjectResponse;
import com.shipit.backend.project.dto.ProjectSummaryResponse;
import com.shipit.backend.project.dto.UpdateProjectRequest;
import com.shipit.backend.project.service.ProjectService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

    @PostMapping
    public ResponseEntity<ApiResponse<ProjectResponse>> createProject(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @Valid @RequestBody CreateProjectRequest request
    ) {
        log.info("Received request to create project '{}' from user: {}", request.getName(), currentUser.getId());
        ProjectResponse response = projectService.createProject(currentUser.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Project created successfully", response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ProjectSummaryResponse>>> getUserProjects(
            @AuthenticationPrincipal UserPrincipal currentUser
    ) {
        log.debug("Received request to list projects for user: {}", currentUser.getId());
        List<ProjectSummaryResponse> response = projectService.getUserProjects(currentUser.getId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProjectResponse>> getProject(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable UUID id
    ) {
        log.debug("Received request to get project id: {} for user: {}", id, currentUser.getId());
        ProjectResponse response = projectService.getProject(currentUser.getId(), id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ProjectResponse>> updateProject(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateProjectRequest request
    ) {
        log.info("Received request to update project id: {} for user: {}", id, currentUser.getId());
        ProjectResponse response = projectService.updateProject(currentUser.getId(), id, request);
        return ResponseEntity.ok(ApiResponse.success("Project updated successfully", response));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteProject(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable UUID id
    ) {
        log.info("Received request to delete project id: {} for user: {}", id, currentUser.getId());
        projectService.deleteProject(currentUser.getId(), id);
        return ResponseEntity.ok(ApiResponse.message("Project deleted successfully"));
    }
}

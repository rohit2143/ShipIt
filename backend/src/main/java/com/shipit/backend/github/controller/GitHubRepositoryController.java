package com.shipit.backend.github.controller;

import com.shipit.backend.auth.security.UserPrincipal;
import com.shipit.backend.common.response.ApiResponse;
import com.shipit.backend.github.dto.*;
import com.shipit.backend.github.service.GitHubRepositoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/github")
@RequiredArgsConstructor
public class GitHubRepositoryController {

    private final GitHubRepositoryService gitHubRepositoryService;

    @GetMapping("/connections/{connectionId}/repositories")
    public ResponseEntity<ApiResponse<GitHubRepositoriesListResponse>> listInstallationRepositories(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable UUID connectionId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "30") int perPage
    ) {
        GitHubRepositoriesListResponse response =
                gitHubRepositoryService.listInstallationRepositories(currentUser.getId(), connectionId, page, perPage);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/connections/{connectionId}/repositories/{owner}/{repo}/branches")
    public ResponseEntity<ApiResponse<List<GitHubBranchResponse>>> listInstallationBranches(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable UUID connectionId,
            @PathVariable String owner,
            @PathVariable String repo,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "100") int perPage
    ) {
        List<GitHubBranchResponse> response = gitHubRepositoryService.listInstallationRepositoryBranches(
                currentUser.getId(), connectionId, owner, repo, page, perPage
        );
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/repositories")
    public ResponseEntity<ApiResponse<GitHubRepositoryResponse>> connectRepository(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @Valid @RequestBody ConnectRepositoryRequest request
    ) {
        GitHubRepositoryResponse response =
                gitHubRepositoryService.connectRepository(currentUser.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                "Repository connected successfully",
                response
        ));
    }

    @GetMapping("/repositories")
    public ResponseEntity<ApiResponse<List<GitHubRepositoryResponse>>> getUserRepositories(
            @AuthenticationPrincipal UserPrincipal currentUser
    ) {
        List<GitHubRepositoryResponse> response =
                gitHubRepositoryService.getUserRepositories(currentUser.getId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/repositories/{id}")
    public ResponseEntity<ApiResponse<GitHubRepositoryResponse>> getUserRepository(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable UUID id
    ) {
        GitHubRepositoryResponse response =
                gitHubRepositoryService.getUserRepository(currentUser.getId(), id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/repositories/{id}/branches")
    public ResponseEntity<ApiResponse<List<GitHubBranchResponse>>> listRepositoryBranches(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable UUID id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "100") int perPage
    ) {
        List<GitHubBranchResponse> response =
                gitHubRepositoryService.listRepositoryBranches(currentUser.getId(), id, page, perPage);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @DeleteMapping("/repositories/{id}")
    public ResponseEntity<ApiResponse<Void>> disconnectRepository(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable UUID id
    ) {
        gitHubRepositoryService.disconnectRepository(currentUser.getId(), id);
        return ResponseEntity.ok(ApiResponse.message("Repository disconnected successfully"));
    }
}

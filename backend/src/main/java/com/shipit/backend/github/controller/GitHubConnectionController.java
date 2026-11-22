package com.shipit.backend.github.controller;

import com.shipit.backend.auth.security.UserPrincipal;
import com.shipit.backend.common.response.ApiResponse;
import com.shipit.backend.github.dto.GitHubCallbackRequest;
import com.shipit.backend.github.dto.GitHubConnectionResponse;
import com.shipit.backend.github.dto.GitHubInstallUrlResponse;
import com.shipit.backend.github.service.GitHubConnectionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/github/connections")
@RequiredArgsConstructor
public class GitHubConnectionController {

    private final GitHubConnectionService gitHubConnectionService;

    @GetMapping("/install-url")
    public ResponseEntity<ApiResponse<GitHubInstallUrlResponse>> getInstallUrl(
            @AuthenticationPrincipal UserPrincipal currentUser
    ) {
        GitHubInstallUrlResponse response = gitHubConnectionService.createInstallationUrl(currentUser.getId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/callback")
    public ResponseEntity<ApiResponse<GitHubConnectionResponse>> handleCallback(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @Valid @RequestBody GitHubCallbackRequest request
    ) {
        GitHubConnectionResponse response =
                gitHubConnectionService.handleInstallationCallback(currentUser.getId(), request);
        return ResponseEntity.ok(ApiResponse.success(
                "GitHub App installation registered and pending verification",
                response
        ));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<GitHubConnectionResponse>>> getConnections(
            @AuthenticationPrincipal UserPrincipal currentUser
    ) {
        List<GitHubConnectionResponse> connections =
                gitHubConnectionService.getUserConnections(currentUser.getId());
        return ResponseEntity.ok(ApiResponse.success(connections));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<GitHubConnectionResponse>> getConnection(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable UUID id
    ) {
        GitHubConnectionResponse response = gitHubConnectionService.getConnection(currentUser.getId(), id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/{id}/verify")
    public ResponseEntity<ApiResponse<GitHubConnectionResponse>> verifyConnection(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable UUID id
    ) {
        GitHubConnectionResponse response = gitHubConnectionService.verifyConnection(currentUser.getId(), id);
        return ResponseEntity.ok(ApiResponse.success(
                "GitHub App installation verified successfully",
                response
        ));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> disconnectConnection(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable UUID id
    ) {
        gitHubConnectionService.disconnectInstallation(currentUser.getId(), id);
        return ResponseEntity.ok(ApiResponse.message("GitHub App installation disconnected successfully"));
    }
}

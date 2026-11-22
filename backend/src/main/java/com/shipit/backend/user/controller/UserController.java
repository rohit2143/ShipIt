package com.shipit.backend.user.controller;

import com.shipit.backend.auth.dto.OAuthLoginRequest;
import com.shipit.backend.auth.entity.AuthProvider;
import com.shipit.backend.auth.security.UserPrincipal;
import com.shipit.backend.auth.service.OAuthService;
import com.shipit.backend.common.response.ApiResponse;
import com.shipit.backend.user.dto.IdentityResponse;
import com.shipit.backend.user.dto.UserResponse;
import com.shipit.backend.user.entity.User;
import com.shipit.backend.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final OAuthService oAuthService;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> getCurrentUser(
            @AuthenticationPrincipal UserPrincipal currentUser
    ) {
        User user = userService.getById(currentUser.getId());
        return ResponseEntity.ok(ApiResponse.success(UserResponse.fromUser(user)));
    }

    @GetMapping("/me/identities")
    public ResponseEntity<ApiResponse<List<IdentityResponse>>> getIdentities(
            @AuthenticationPrincipal UserPrincipal currentUser
    ) {
        List<IdentityResponse> identities = oAuthService.getUserIdentities(currentUser.getId());
        return ResponseEntity.ok(ApiResponse.success(identities));
    }

    @PostMapping("/me/identities/github/link")
    public ResponseEntity<ApiResponse<IdentityResponse>> linkGitHub(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @Valid @RequestBody OAuthLoginRequest request
    ) {
        IdentityResponse response = oAuthService.linkGitHubAccount(
                currentUser.getId(),
                request.getCode(),
                request.getRedirectUri()
        );
        return ResponseEntity.ok(ApiResponse.success("GitHub account linked successfully", response));
    }

    @DeleteMapping("/me/identities/{provider}")
    public ResponseEntity<ApiResponse<Void>> unlinkIdentity(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable AuthProvider provider
    ) {
        oAuthService.unlinkIdentity(currentUser.getId(), provider);
        return ResponseEntity.ok(ApiResponse.message("Identity unlinked successfully"));
    }
}


package com.shipit.backend.auth.controller;

import com.shipit.backend.auth.dto.AuthResponse;
import com.shipit.backend.auth.dto.OAuthLoginRequest;
import com.shipit.backend.auth.service.OAuthService;
import com.shipit.backend.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth/oauth")
@RequiredArgsConstructor
public class OAuthController {

    private final OAuthService oAuthService;

    @PostMapping("/github")
    public ResponseEntity<ApiResponse<AuthResponse>> loginWithGitHub(
            @Valid @RequestBody OAuthLoginRequest request,
            HttpServletRequest servletRequest
    ) {
        String userAgent = servletRequest.getHeader("User-Agent");
        String ipAddress = extractClientIp(servletRequest);

        AuthResponse response = oAuthService.authenticateWithGitHub(request, userAgent, ipAddress);
        return ResponseEntity.ok(ApiResponse.success("GitHub authentication successful", response));
    }

    private String extractClientIp(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader == null || xfHeader.isBlank()) {
            return request.getRemoteAddr();
        }
        return xfHeader.split(",")[0].trim();
    }
}

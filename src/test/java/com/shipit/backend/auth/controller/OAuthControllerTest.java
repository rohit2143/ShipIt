package com.shipit.backend.auth.controller;

import com.shipit.backend.auth.dto.AuthResponse;
import com.shipit.backend.auth.dto.OAuthLoginRequest;
import com.shipit.backend.auth.dto.TokenResponse;
import com.shipit.backend.auth.service.OAuthService;
import com.shipit.backend.common.exception.ConflictException;
import com.shipit.backend.common.exception.GlobalExceptionHandler;
import com.shipit.backend.user.dto.UserResponse;
import com.shipit.backend.user.entity.Role;
import com.shipit.backend.user.entity.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class OAuthControllerTest {

    private MockMvc mockMvc;

    @Mock
    private OAuthService oAuthService;

    @InjectMocks
    private OAuthController oAuthController;

    private AuthResponse sampleAuthResponse;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(oAuthController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        UserResponse userResponse = UserResponse.builder()
                .id(UUID.randomUUID())
                .name("Octo Dev")
                .email("octo@github.com")
                .role(Role.ROLE_USER)
                .status(UserStatus.ACTIVE)
                .emailVerified(true)
                .createdAt(Instant.now())
                .build();

        TokenResponse tokenResponse = TokenResponse.builder()
                .accessToken("gh-jwt-token")
                .refreshToken("gh-refresh-token")
                .tokenType("Bearer")
                .expiresIn(900L)
                .build();

        sampleAuthResponse = AuthResponse.builder()
                .user(userResponse)
                .tokens(tokenResponse)
                .build();
    }

    @Test
    @DisplayName("Should return 200 OK on successful GitHub OAuth login")
    void shouldLoginWithGitHubSuccessfully() throws Exception {
        String payload = """
                {
                    "code": "valid-gh-code-123",
                    "redirectUri": "http://localhost:5173/auth/callback/github"
                }
                """;

        when(oAuthService.authenticateWithGitHub(any(OAuthLoginRequest.class), any(), any()))
                .thenReturn(sampleAuthResponse);

        mockMvc.perform(post("/api/v1/auth/oauth/github")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("GitHub authentication successful"))
                .andExpect(jsonPath("$.data.user.email").value("octo@github.com"))
                .andExpect(jsonPath("$.data.tokens.accessToken").value("gh-jwt-token"));
    }

    @Test
    @DisplayName("Should return 400 Bad Request when GitHub OAuth code is blank")
    void shouldFailValidationWhenOAuthCodeIsBlank() throws Exception {
        String payload = """
                {
                    "code": "",
                    "redirectUri": "http://localhost:5173/auth/callback/github"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/oauth/github")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[*].field", hasItem("code")));

        verifyNoInteractions(oAuthService);
    }

    @Test
    @DisplayName("Should return 409 Conflict when local account exists without linked GitHub identity")
    void shouldReturnConflictWhenEmailExistsLocally() throws Exception {
        String payload = """
                {
                    "code": "valid-gh-code-123"
                }
                """;

        when(oAuthService.authenticateWithGitHub(any(OAuthLoginRequest.class), any(), any()))
                .thenThrow(new ConflictException(
                        "An account with this email already exists. Please log in with your email and password to link your GitHub account.",
                        "ACCOUNT_EXISTS_LINK_REQUIRED"
                ));

        mockMvc.perform(post("/api/v1/auth/oauth/github")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("ACCOUNT_EXISTS_LINK_REQUIRED"))
                .andExpect(jsonPath("$.message").value(containsString("Please log in with your email and password")));
    }
}

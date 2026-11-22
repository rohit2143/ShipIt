package com.shipit.backend.auth.controller;

import com.shipit.backend.auth.dto.*;
import com.shipit.backend.auth.service.AuthService;
import com.shipit.backend.auth.service.SecurityTokenService;
import com.shipit.backend.common.exception.*;
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
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    private MockMvc mockMvc;

    @Mock
    private AuthService authService;

    @Mock
    private SecurityTokenService securityTokenService;

    @InjectMocks
    private AuthController authController;

    private AuthResponse sampleAuthResponse;
    private TokenResponse sampleTokenResponse;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(authController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        UserResponse userResponse = UserResponse.builder()
                .id(UUID.randomUUID())
                .name("Alice User")
                .email("alice@example.com")
                .role(Role.ROLE_USER)
                .status(UserStatus.ACTIVE)
                .emailVerified(false)
                .createdAt(Instant.now())
                .build();

        sampleTokenResponse = TokenResponse.builder()
                .accessToken("access-token-xyz")
                .refreshToken("refresh-token-abc")
                .tokenType("Bearer")
                .expiresIn(900L)
                .build();

        sampleAuthResponse = AuthResponse.builder()
                .user(userResponse)
                .tokens(sampleTokenResponse)
                .build();
    }

    @Test
    @DisplayName("Should return 201 Created on successful registration")
    void shouldRegisterUserSuccessfully() throws Exception {
        String payload = """
                {
                    "name": "Alice User",
                    "email": "alice@example.com",
                    "password": "securepassword123"
                }
                """;

        when(authService.register(any(RegisterRequest.class), any(), any()))
                .thenReturn(sampleAuthResponse);

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("User registered successfully"))
                .andExpect(jsonPath("$.data.user.email").value("alice@example.com"))
                .andExpect(jsonPath("$.data.tokens.accessToken").value("access-token-xyz"));
    }

    @Test
    @DisplayName("Should return 400 Bad Request when register payload has invalid email")
    void shouldFailValidationOnRegisterWithInvalidEmail() throws Exception {
        String payload = """
                {
                    "name": "Alice User",
                    "email": "not-an-email",
                    "password": "securepassword123"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[*].field", hasItem("email")));

        verifyNoInteractions(authService);
    }

    @Test
    @DisplayName("Should return 400 Bad Request when register password is too short")
    void shouldFailValidationOnRegisterWithShortPassword() throws Exception {
        String payload = """
                {
                    "name": "Alice User",
                    "email": "alice@example.com",
                    "password": "short"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[*].field", hasItem("password")));

        verifyNoInteractions(authService);
    }

    @Test
    @DisplayName("Should return 409 Conflict when registering with existing email")
    void shouldReturnConflictWhenEmailAlreadyExists() throws Exception {
        String payload = """
                {
                    "name": "Alice User",
                    "email": "alice@example.com",
                    "password": "securepassword123"
                }
                """;

        when(authService.register(any(RegisterRequest.class), any(), any()))
                .thenThrow(new ConflictException("An account with this email already exists", "EMAIL_ALREADY_EXISTS"));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("EMAIL_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.message").value("An account with this email already exists"));
    }

    @Test
    @DisplayName("Should return 200 OK on valid credentials login")
    void shouldLoginSuccessfully() throws Exception {
        String payload = """
                {
                    "email": "alice@example.com",
                    "password": "securepassword123"
                }
                """;

        when(authService.login(any(LoginRequest.class), any(), any()))
                .thenReturn(sampleAuthResponse);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.tokens.accessToken").value("access-token-xyz"));
    }

    @Test
    @DisplayName("Should return 401 Unauthorized when credentials are invalid")
    void shouldReturnUnauthorizedOnInvalidCredentials() throws Exception {
        String payload = """
                {
                    "email": "alice@example.com",
                    "password": "wrongpassword"
                }
                """;

        when(authService.login(any(LoginRequest.class), any(), any()))
                .thenThrow(new UnauthorizedException("Invalid email or password", "INVALID_CREDENTIALS"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("INVALID_CREDENTIALS"));
    }

    @Test
    @DisplayName("Should return 403 Forbidden when account is suspended")
    void shouldReturnForbiddenWhenAccountSuspended() throws Exception {
        String payload = """
                {
                    "email": "alice@example.com",
                    "password": "securepassword123"
                }
                """;

        when(authService.login(any(LoginRequest.class), any(), any()))
                .thenThrow(new ForbiddenException("Account has been suspended"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("Should return 200 OK with rotated tokens on valid refresh request")
    void shouldRefreshTokenSuccessfully() throws Exception {
        String payload = """
                {
                    "refreshToken": "valid-refresh-token"
                }
                """;

        when(authService.refreshToken(any(RefreshTokenRequest.class), any(), any()))
                .thenReturn(sampleTokenResponse);

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").value("access-token-xyz"))
                .andExpect(jsonPath("$.data.refreshToken").value("refresh-token-abc"));
    }

    @Test
    @DisplayName("Should return 401 Unauthorized when refreshing with revoked token")
    void shouldReturnUnauthorizedOnRevokedRefreshToken() throws Exception {
        String payload = """
                {
                    "refreshToken": "compromised-token"
                }
                """;

        when(authService.refreshToken(any(RefreshTokenRequest.class), any(), any()))
                .thenThrow(new UnauthorizedException("Refresh token reuse detected. All tokens revoked.", "TOKEN_REUSE_DETECTED"));

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("TOKEN_REUSE_DETECTED"));
    }

    @Test
    @DisplayName("Should return 200 OK on logout")
    void shouldLogoutSuccessfully() throws Exception {
        String payload = """
                {
                    "refreshToken": "token-to-revoke"
                }
                """;

        doNothing().when(authService).logout("token-to-revoke");

        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Logged out successfully"));

        verify(authService).logout("token-to-revoke");
    }

    @Test
    @DisplayName("Should return 200 OK on valid email verification")
    void shouldVerifyEmailSuccessfully() throws Exception {
        String payload = """
                {
                    "token": "valid-verification-token"
                }
                """;

        doNothing().when(securityTokenService).verifyEmail("valid-verification-token");

        mockMvc.perform(post("/api/v1/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Email verified successfully"));
    }

    @Test
    @DisplayName("Should return 400 Bad Request on invalid verification token")
    void shouldReturnBadRequestOnInvalidVerificationToken() throws Exception {
        String payload = """
                {
                    "token": "expired-token"
                }
                """;

        doThrow(new BadRequestException("Verification token has expired", "TOKEN_EXPIRED"))
                .when(securityTokenService).verifyEmail("expired-token");

        mockMvc.perform(post("/api/v1/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("TOKEN_EXPIRED"));
    }

    @Test
    @DisplayName("Should return 200 OK on forgot password request")
    void shouldHandleForgotPassword() throws Exception {
        String payload = """
                {
                    "email": "alice@example.com"
                }
                """;

        when(securityTokenService.createPasswordResetToken("alice@example.com"))
                .thenReturn(Optional.of("sample-reset-token"));

        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value(containsString("password reset instructions have been dispatched")));
    }

    @Test
    @DisplayName("Should return 200 OK on reset password")
    void shouldResetPasswordSuccessfully() throws Exception {
        String payload = """
                {
                    "token": "valid-reset-token",
                    "newPassword": "newSecurePassword123"
                }
                """;

        doNothing().when(securityTokenService).resetPassword("valid-reset-token", "newSecurePassword123");

        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value(containsString("Password has been reset successfully")));
    }
}

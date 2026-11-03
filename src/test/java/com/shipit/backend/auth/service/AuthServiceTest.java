package com.shipit.backend.auth.service;

import com.shipit.backend.auth.dto.AuthResponse;
import com.shipit.backend.auth.dto.LoginRequest;
import com.shipit.backend.auth.dto.RegisterRequest;
import com.shipit.backend.auth.entity.AuthProvider;
import com.shipit.backend.auth.entity.RefreshToken;
import com.shipit.backend.auth.entity.UserAuthIdentity;
import com.shipit.backend.auth.repository.UserAuthIdentityRepository;
import com.shipit.backend.common.exception.ConflictException;
import com.shipit.backend.common.exception.UnauthorizedException;
import com.shipit.backend.config.JwtProperties;
import com.shipit.backend.user.entity.Role;
import com.shipit.backend.user.entity.User;
import com.shipit.backend.user.entity.UserStatus;
import com.shipit.backend.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserService userService;

    @Mock
    private UserAuthIdentityRepository identityRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private JwtProperties jwtProperties;

    @InjectMocks
    private AuthService authService;

    private User sampleUser;

    @BeforeEach
    void setUp() {
        sampleUser = User.builder()
                .id(UUID.randomUUID())
                .name("Sarah Connor")
                .email("sarah@shipit.dev")
                .role(Role.ROLE_USER)
                .status(UserStatus.ACTIVE)
                .emailVerified(false)
                .build();
    }

    @Test
    @DisplayName("Should successfully register a new user and generate auth tokens")
    void shouldRegisterNewUser() {
        RegisterRequest request = RegisterRequest.builder()
                .name("Sarah Connor")
                .email("sarah@shipit.dev")
                .password("Password123!")
                .build();

        when(userService.existsByEmail("sarah@shipit.dev")).thenReturn(false);
        when(userService.createUser("Sarah Connor", "sarah@shipit.dev", Role.ROLE_USER, UserStatus.ACTIVE, false))
                .thenReturn(sampleUser);
        when(passwordEncoder.encode("Password123!")).thenReturn("encoded_pass");
        when(jwtService.generateAccessToken(sampleUser)).thenReturn("sample.access.jwt");
        when(jwtProperties.getAccessTokenExpirationSeconds()).thenReturn(900L);

        RefreshToken refreshTokenEntity = RefreshToken.builder().build();
        when(refreshTokenService.createRefreshToken(eq(sampleUser), any(), any()))
                .thenReturn(new RefreshTokenService.RotatedToken("sample-refresh-token", refreshTokenEntity));

        AuthResponse response = authService.register(request, "Agent/1.0", "127.0.0.1");

        assertThat(response).isNotNull();
        assertThat(response.getUser().getEmail()).isEqualTo("sarah@shipit.dev");
        assertThat(response.getTokens().getAccessToken()).isEqualTo("sample.access.jwt");
        assertThat(response.getTokens().getRefreshToken()).isEqualTo("sample-refresh-token");

        verify(identityRepository).save(any(UserAuthIdentity.class));
    }

    @Test
    @DisplayName("Should throw ConflictException when registering with an existing email")
    void shouldThrowConflictWhenEmailExists() {
        RegisterRequest request = RegisterRequest.builder()
                .name("Duplicate User")
                .email("sarah@shipit.dev")
                .password("Password123!")
                .build();

        when(userService.existsByEmail("sarah@shipit.dev")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request, "Agent/1.0", "127.0.0.1"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("An account with this email already exists");

        verifyNoInteractions(passwordEncoder, identityRepository);
    }

    @Test
    @DisplayName("Should successfully authenticate valid credentials and issue tokens")
    void shouldLoginWithValidCredentials() {
        LoginRequest request = LoginRequest.builder()
                .email("sarah@shipit.dev")
                .password("Password123!")
                .build();

        UserAuthIdentity identity = UserAuthIdentity.builder()
                .user(sampleUser)
                .provider(AuthProvider.LOCAL)
                .providerUserId("sarah@shipit.dev")
                .passwordHash("encoded_pass")
                .build();

        when(identityRepository.findByProviderAndProviderUserId(AuthProvider.LOCAL, "sarah@shipit.dev"))
                .thenReturn(Optional.of(identity));
        when(passwordEncoder.matches("Password123!", "encoded_pass")).thenReturn(true);
        when(jwtService.generateAccessToken(sampleUser)).thenReturn("access.jwt");
        when(jwtProperties.getAccessTokenExpirationSeconds()).thenReturn(900L);

        RefreshToken refreshTokenEntity = RefreshToken.builder().build();
        when(refreshTokenService.createRefreshToken(eq(sampleUser), any(), any()))
                .thenReturn(new RefreshTokenService.RotatedToken("refresh.token", refreshTokenEntity));

        AuthResponse response = authService.login(request, "Agent/1.0", "127.0.0.1");

        assertThat(response.getUser().getEmail()).isEqualTo("sarah@shipit.dev");
        assertThat(response.getTokens().getAccessToken()).isEqualTo("access.jwt");
    }

    @Test
    @DisplayName("Should reject invalid password with UnauthorizedException")
    void shouldRejectInvalidPassword() {
        LoginRequest request = LoginRequest.builder()
                .email("sarah@shipit.dev")
                .password("WrongPassword!")
                .build();

        UserAuthIdentity identity = UserAuthIdentity.builder()
                .user(sampleUser)
                .provider(AuthProvider.LOCAL)
                .providerUserId("sarah@shipit.dev")
                .passwordHash("encoded_pass")
                .build();

        when(identityRepository.findByProviderAndProviderUserId(AuthProvider.LOCAL, "sarah@shipit.dev"))
                .thenReturn(Optional.of(identity));
        when(passwordEncoder.matches("WrongPassword!", "encoded_pass")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(request, "Agent/1.0", "127.0.0.1"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Invalid email or password");
    }
}

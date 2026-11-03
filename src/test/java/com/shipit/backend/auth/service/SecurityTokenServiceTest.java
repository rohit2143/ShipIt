package com.shipit.backend.auth.service;

import com.shipit.backend.auth.entity.AuthProvider;
import com.shipit.backend.auth.entity.SecurityToken;
import com.shipit.backend.auth.entity.SecurityTokenType;
import com.shipit.backend.auth.entity.UserAuthIdentity;
import com.shipit.backend.auth.repository.SecurityTokenRepository;
import com.shipit.backend.auth.repository.UserAuthIdentityRepository;
import com.shipit.backend.common.exception.BadRequestException;
import com.shipit.backend.common.util.HashUtil;
import com.shipit.backend.user.entity.Role;
import com.shipit.backend.user.entity.User;
import com.shipit.backend.user.entity.UserStatus;
import com.shipit.backend.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SecurityTokenServiceTest {

    @Mock
    private SecurityTokenRepository securityTokenRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserAuthIdentityRepository identityRepository;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private SecurityTokenService securityTokenService;

    private User sampleUser;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(securityTokenService, "emailVerificationExpirationSeconds", 86400L);
        ReflectionTestUtils.setField(securityTokenService, "passwordResetExpirationSeconds", 3600L);

        sampleUser = User.builder()
                .id(UUID.randomUUID())
                .name("John Connor")
                .email("john@shipit.dev")
                .role(Role.ROLE_USER)
                .status(UserStatus.ACTIVE)
                .emailVerified(false)
                .build();
    }

    @Test
    @DisplayName("Should create email verification token, hash it, and invalidate prior tokens")
    void shouldCreateEmailVerificationToken() {
        when(securityTokenRepository.save(any(SecurityToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        String rawToken = securityTokenService.createEmailVerificationToken(sampleUser);

        assertThat(rawToken).isNotBlank();
        verify(securityTokenRepository).invalidateAllActiveByUserIdAndType(
                eq(sampleUser.getId()),
                eq(SecurityTokenType.EMAIL_VERIFICATION),
                any(Instant.class)
        );
        verify(securityTokenRepository).save(any(SecurityToken.class));
    }

    @Test
    @DisplayName("Should successfully verify email and mark user as verified")
    void shouldVerifyEmailSuccessfully() {
        String rawToken = "raw-verification-token-123";
        String tokenHash = HashUtil.sha256(rawToken);

        SecurityToken token = SecurityToken.builder()
                .id(UUID.randomUUID())
                .user(sampleUser)
                .tokenType(SecurityTokenType.EMAIL_VERIFICATION)
                .tokenHash(tokenHash)
                .expiresAt(Instant.now().plusSeconds(3600))
                .usedAt(null)
                .build();

        when(securityTokenRepository.findByTokenHashAndTokenType(tokenHash, SecurityTokenType.EMAIL_VERIFICATION))
                .thenReturn(Optional.of(token));

        securityTokenService.verifyEmail(rawToken);

        assertThat(token.getUsedAt()).isNotNull();
        assertThat(sampleUser.isEmailVerified()).isTrue();
        verify(securityTokenRepository).save(token);
        verify(userRepository).save(sampleUser);
    }

    @Test
    @DisplayName("Should reject email verification with expired token")
    void shouldRejectExpiredVerificationToken() {
        String rawToken = "expired-token-123";
        String tokenHash = HashUtil.sha256(rawToken);

        SecurityToken token = SecurityToken.builder()
                .id(UUID.randomUUID())
                .user(sampleUser)
                .tokenType(SecurityTokenType.EMAIL_VERIFICATION)
                .tokenHash(tokenHash)
                .expiresAt(Instant.now().minusSeconds(100))
                .usedAt(null)
                .build();

        when(securityTokenRepository.findByTokenHashAndTokenType(tokenHash, SecurityTokenType.EMAIL_VERIFICATION))
                .thenReturn(Optional.of(token));

        assertThatThrownBy(() -> securityTokenService.verifyEmail(rawToken))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("expired");
    }

    @Test
    @DisplayName("Should return empty Optional without error when forgot password requested for unknown email")
    void shouldReturnEmptyForUnknownEmailOnForgotPassword() {
        when(userRepository.findByEmail("unknown@shipit.dev")).thenReturn(Optional.empty());

        Optional<String> token = securityTokenService.createPasswordResetToken("unknown@shipit.dev");

        assertThat(token).isEmpty();
        verifyNoInteractions(securityTokenRepository);
    }

    @Test
    @DisplayName("Should reset password, update hash, and terminate all active user refresh sessions")
    void shouldResetPasswordAndRevokeActiveSessions() {
        String rawToken = "reset-token-xyz";
        String tokenHash = HashUtil.sha256(rawToken);

        SecurityToken token = SecurityToken.builder()
                .id(UUID.randomUUID())
                .user(sampleUser)
                .tokenType(SecurityTokenType.PASSWORD_RESET)
                .tokenHash(tokenHash)
                .expiresAt(Instant.now().plusSeconds(1800))
                .usedAt(null)
                .build();

        UserAuthIdentity localIdentity = UserAuthIdentity.builder()
                .user(sampleUser)
                .provider(AuthProvider.LOCAL)
                .providerUserId("john@shipit.dev")
                .passwordHash("old_hash")
                .build();

        when(securityTokenRepository.findByTokenHashAndTokenType(tokenHash, SecurityTokenType.PASSWORD_RESET))
                .thenReturn(Optional.of(token));
        when(identityRepository.findByUserIdAndProvider(sampleUser.getId(), AuthProvider.LOCAL))
                .thenReturn(Optional.of(localIdentity));
        when(passwordEncoder.encode("BrandNewPassword123!")).thenReturn("new_encoded_hash");

        securityTokenService.resetPassword(rawToken, "BrandNewPassword123!");

        assertThat(token.getUsedAt()).isNotNull();
        assertThat(localIdentity.getPasswordHash()).isEqualTo("new_encoded_hash");

        verify(identityRepository).save(localIdentity);
        verify(securityTokenRepository).save(token);
        // Verify all active refresh tokens for user are revoked
        verify(refreshTokenService).revokeAllUserTokens(sampleUser.getId());
    }
}

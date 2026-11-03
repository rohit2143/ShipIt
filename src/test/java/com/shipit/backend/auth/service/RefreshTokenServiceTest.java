package com.shipit.backend.auth.service;

import com.shipit.backend.auth.entity.RefreshToken;
import com.shipit.backend.auth.repository.RefreshTokenRepository;
import com.shipit.backend.common.exception.UnauthorizedException;
import com.shipit.backend.common.util.HashUtil;
import com.shipit.backend.config.JwtProperties;
import com.shipit.backend.user.entity.Role;
import com.shipit.backend.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private JwtProperties jwtProperties;

    @InjectMocks
    private RefreshTokenService refreshTokenService;

    private User sampleUser;

    @BeforeEach
    void setUp() {
        sampleUser = User.builder()
                .id(UUID.randomUUID())
                .email("test@shipit.dev")
                .role(Role.ROLE_USER)
                .build();
    }

    @Test
    @DisplayName("Should create refresh token with hashed value and family ID")
    void shouldCreateRefreshToken() {
        when(jwtProperties.getRefreshTokenExpirationSeconds()).thenReturn(604800L);
        when(refreshTokenRepository.save(any(RefreshToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        RefreshTokenService.RotatedToken result =
                refreshTokenService.createRefreshToken(sampleUser, "Mozilla/5.0", "127.0.0.1");

        assertThat(result.rawToken()).isNotBlank();
        assertThat(result.entity().getTokenHash()).isEqualTo(HashUtil.sha256(result.rawToken()));
        assertThat(result.entity().getFamilyId()).isNotNull();
        assertThat(result.entity().getUserAgent()).isEqualTo("Mozilla/5.0");
        assertThat(result.entity().getIpAddress()).isEqualTo("127.0.0.1");
        assertThat(result.entity().getRevokedAt()).isNull();

        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    @DisplayName("Should successfully rotate active refresh token maintaining the same family ID")
    void shouldRotateActiveRefreshToken() {
        UUID familyId = UUID.randomUUID();
        String rawToken = "current-raw-token-123";
        String tokenHash = HashUtil.sha256(rawToken);

        RefreshToken existingToken = RefreshToken.builder()
                .id(UUID.randomUUID())
                .user(sampleUser)
                .tokenHash(tokenHash)
                .familyId(familyId)
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        when(jwtProperties.getRefreshTokenExpirationSeconds()).thenReturn(604800L);
        when(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(existingToken));
        when(refreshTokenRepository.save(any(RefreshToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        RefreshTokenService.RotatedToken rotated =
                refreshTokenService.rotateRefreshToken(rawToken, "Chrome/120", "192.168.1.1");

        assertThat(rotated.rawToken()).isNotEqualTo(rawToken);
        assertThat(rotated.entity().getFamilyId()).isEqualTo(familyId);
        assertThat(existingToken.getRevokedAt()).isNotNull();

        verify(refreshTokenRepository, times(2)).save(any(RefreshToken.class));
    }

    @Test
    @DisplayName("Should trigger reuse detection and revoke entire token family when already revoked token is used")
    void shouldTriggerReuseDetectionOnRevokedToken() {
        UUID familyId = UUID.randomUUID();
        String stolenToken = "already-revoked-token";
        String tokenHash = HashUtil.sha256(stolenToken);

        RefreshToken revokedToken = RefreshToken.builder()
                .id(UUID.randomUUID())
                .user(sampleUser)
                .tokenHash(tokenHash)
                .familyId(familyId)
                .expiresAt(Instant.now().plusSeconds(3600))
                .revokedAt(Instant.now().minusSeconds(100))
                .build();

        when(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(revokedToken));

        assertThatThrownBy(() -> refreshTokenService.rotateRefreshToken(stolenToken, "AttackerBrowser", "10.0.0.1"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Revoked refresh token reuse detected");

        verify(refreshTokenRepository).revokeAllByFamilyId(eq(familyId), any(Instant.class));
    }
}

package com.shipit.backend.github.service;

import com.shipit.backend.common.exception.AppException;
import com.shipit.backend.config.GitHubAppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GitHubAppAuthServiceTest {

    @Mock
    private GitHubAppProperties properties;

    @Mock
    private GitHubAppKeyLoader keyLoader;

    @InjectMocks
    private GitHubAppAuthService authService;

    private KeyPair keyPair;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        keyPair = kpg.generateKeyPair();
    }

    @Test
    @DisplayName("Should generate valid RS256 JWT signed with RSA private key")
    void shouldGenerateValidRs256Jwt() {
        when(properties.getAppId()).thenReturn("123456");
        when(keyLoader.loadPrivateKey()).thenReturn((RSAPrivateKey) keyPair.getPrivate());

        Instant before = Instant.now();
        String jwt = authService.generateAppJwt();
        Instant after = Instant.now();

        assertThat(jwt).isNotBlank();

        // Cryptographically verify with the matching public key
        Jws<Claims> parsed = Jwts.parser()
                .verifyWith(keyPair.getPublic())
                .build()
                .parseSignedClaims(jwt);

        assertThat(parsed.getHeader().getAlgorithm()).isEqualTo("RS256");
        assertThat(parsed.getHeader().getType()).isEqualTo("JWT");

        Claims claims = parsed.getPayload();
        assertThat(claims.getIssuer()).isEqualTo("123456");

        Date issuedAt = claims.getIssuedAt();
        Date expiration = claims.getExpiration();

        // iat should be ~60 seconds before now
        assertThat(issuedAt.toInstant()).isBefore(before);
        assertThat(issuedAt.toInstant()).isAfter(before.minusSeconds(70));

        // exp should be ~9 minutes after now
        assertThat(expiration.toInstant()).isAfter(after.plusSeconds(8 * 60));
        assertThat(expiration.toInstant()).isBefore(after.plusSeconds(10 * 60));
    }

    @Test
    @DisplayName("Should throw GITHUB_APP_ID_NOT_CONFIGURED when appId is null or blank")
    void shouldThrowWhenAppIdNotConfigured() {
        when(properties.getAppId()).thenReturn("");

        assertThatThrownBy(() -> authService.generateAppJwt())
                .isInstanceOf(AppException.class)
                .hasMessageContaining("App ID is not configured")
                .extracting("errorCode")
                .isEqualTo("GITHUB_APP_ID_NOT_CONFIGURED");
    }
}

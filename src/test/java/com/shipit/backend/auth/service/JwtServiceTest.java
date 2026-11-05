package com.shipit.backend.auth.service;

import com.shipit.backend.config.JwtProperties;
import com.shipit.backend.user.entity.Role;
import com.shipit.backend.user.entity.User;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private JwtService jwtService;
    private JwtProperties jwtProperties;

    @BeforeEach
    void setUp() {
        jwtProperties = new JwtProperties();
        jwtProperties.setSecret("404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970");
        jwtProperties.setAccessTokenExpirationSeconds(900);
        jwtProperties.setRefreshTokenExpirationSeconds(604800);
        jwtService = new JwtService(jwtProperties);
    }

    @Test
    @DisplayName("Should generate valid JWT access token and extract user claims")
    void shouldGenerateAndExtractClaims() {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .id(userId)
                .email("developer@shipit.dev")
                .role(Role.ROLE_USER)
                .build();

        String token = jwtService.generateAccessToken(user);

        assertThat(token).isNotBlank();
        assertThat(jwtService.isTokenValid(token)).isTrue();
        assertThat(jwtService.extractUserId(token)).isEqualTo(userId);
        assertThat(jwtService.extractEmail(token)).isEqualTo("developer@shipit.dev");
        assertThat(jwtService.extractRole(token)).isEqualTo("ROLE_USER");
    }

    @Test
    @DisplayName("Should reject invalid or tampered JWT token")
    void shouldRejectInvalidToken() {
        assertThat(jwtService.isTokenValid("invalid.jwt.token")).isFalse();
        assertThat(jwtService.isTokenValid("")).isFalse();
    }

    @Test
    @DisplayName("Should reject genuinely expired JWT access token")
    void shouldRejectExpiredToken() {
        SecretKey key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtProperties.getSecret()));
        String expiredToken = Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .claim("email", "expired@shipit.dev")
                .claim("role", "ROLE_USER")
                .issuedAt(Date.from(Instant.now().minusSeconds(3600)))
                .expiration(Date.from(Instant.now().minusSeconds(1800)))
                .signWith(key)
                .compact();

        assertThat(jwtService.isTokenValid(expiredToken)).isFalse();
    }

    @Test
    @DisplayName("Should reject JWT access token signed with an untrusted secret")
    void shouldRejectTokenSignedWithUntrustedSecret() {
        SecretKey untrustedKey = Keys.hmacShaKeyFor(
                "AABBCCDDEEFF00112233445566778899AABBCCDDEEFF00112233445566778899".getBytes(StandardCharsets.UTF_8)
        );
        String untrustedToken = Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .claim("email", "hacker@shipit.dev")
                .claim("role", "ROLE_USER")
                .issuedAt(new Date())
                .expiration(Date.from(Instant.now().plusSeconds(900)))
                .signWith(untrustedKey)
                .compact();

        assertThat(jwtService.isTokenValid(untrustedToken)).isFalse();
    }
}

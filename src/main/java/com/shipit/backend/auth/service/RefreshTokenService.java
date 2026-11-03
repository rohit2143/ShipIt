package com.shipit.backend.auth.service;

import com.shipit.backend.auth.entity.RefreshToken;
import com.shipit.backend.auth.repository.RefreshTokenRepository;
import com.shipit.backend.common.exception.UnauthorizedException;
import com.shipit.backend.common.util.HashUtil;
import com.shipit.backend.config.JwtProperties;
import com.shipit.backend.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtProperties jwtProperties;

    public record RotatedToken(String rawToken, RefreshToken entity) {
    }

    @Transactional
    public RotatedToken createRefreshToken(User user, String userAgent, String ipAddress) {
        String rawToken = generateRawToken();
        UUID familyId = UUID.randomUUID();
        String tokenHash = HashUtil.sha256(rawToken);
        Instant expiresAt = Instant.now().plusSeconds(jwtProperties.getRefreshTokenExpirationSeconds());

        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .tokenHash(tokenHash)
                .familyId(familyId)
                .expiresAt(expiresAt)
                .userAgent(userAgent)
                .ipAddress(ipAddress)
                .build();

        RefreshToken saved = refreshTokenRepository.save(refreshToken);
        log.info("Issued new refresh token family {} for user {}", familyId, user.getId());
        return new RotatedToken(rawToken, saved);
    }

    @Transactional
    public RotatedToken rotateRefreshToken(String rawToken, String userAgent, String ipAddress) {
        String tokenHash = HashUtil.sha256(rawToken);
        RefreshToken token = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));

        if (token.isRevoked()) {
            log.warn("SECURITY ALERT: Reuse of revoked refresh token detected for family {}. Revoking all tokens in family.",
                    token.getFamilyId());
            refreshTokenRepository.revokeAllByFamilyId(token.getFamilyId(), Instant.now());
            throw new UnauthorizedException("Revoked refresh token reuse detected. All sessions in this chain have been terminated.");
        }

        if (token.isExpired()) {
            throw new UnauthorizedException("Refresh token has expired");
        }

        // Revoke current token
        token.setRevokedAt(Instant.now());
        refreshTokenRepository.save(token);

        // Generate next descendant in same token family
        String newRawToken = generateRawToken();
        String newTokenHash = HashUtil.sha256(newRawToken);
        Instant expiresAt = Instant.now().plusSeconds(jwtProperties.getRefreshTokenExpirationSeconds());

        RefreshToken nextToken = RefreshToken.builder()
                .user(token.getUser())
                .tokenHash(newTokenHash)
                .familyId(token.getFamilyId())
                .expiresAt(expiresAt)
                .userAgent(userAgent)
                .ipAddress(ipAddress)
                .build();

        RefreshToken saved = refreshTokenRepository.save(nextToken);
        log.info("Rotated refresh token for user {} in family {}", token.getUser().getId(), token.getFamilyId());
        return new RotatedToken(newRawToken, saved);
    }

    @Transactional
    public void revokeToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        String tokenHash = HashUtil.sha256(rawToken);
        refreshTokenRepository.findByTokenHash(tokenHash).ifPresent(token -> {
            token.setRevokedAt(Instant.now());
            refreshTokenRepository.save(token);
            log.info("Revoked refresh token for user {}", token.getUser().getId());
        });
    }

    @Transactional
    public void revokeAllUserTokens(UUID userId) {
        int revokedCount = refreshTokenRepository.revokeAllByUserId(userId, Instant.now());
        log.info("Revoked {} refresh tokens for user {}", revokedCount, userId);
    }

    private String generateRawToken() {
        return UUID.randomUUID().toString() + "-" + UUID.randomUUID().toString();
    }
}

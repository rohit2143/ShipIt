package com.shipit.backend.auth.service;

import com.shipit.backend.auth.entity.AuthProvider;
import com.shipit.backend.auth.entity.SecurityToken;
import com.shipit.backend.auth.entity.SecurityTokenType;
import com.shipit.backend.auth.entity.UserAuthIdentity;
import com.shipit.backend.auth.repository.SecurityTokenRepository;
import com.shipit.backend.auth.repository.UserAuthIdentityRepository;
import com.shipit.backend.common.exception.BadRequestException;
import com.shipit.backend.common.util.HashUtil;
import com.shipit.backend.user.entity.User;
import com.shipit.backend.user.repository.UserRepository;
import com.shipit.backend.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SecurityTokenService {

    private final SecurityTokenRepository securityTokenRepository;
    private final UserRepository userRepository;
    private final UserAuthIdentityRepository identityRepository;
    private final RefreshTokenService refreshTokenService;
    private final PasswordEncoder passwordEncoder;

    @Value("${shipit.auth.email-verification-expiration-seconds:86400}")
    private long emailVerificationExpirationSeconds;

    @Value("${shipit.auth.password-reset-expiration-seconds:3600}")
    private long passwordResetExpirationSeconds;

    @Transactional
    public String createEmailVerificationToken(User user) {
        securityTokenRepository.invalidateAllActiveByUserIdAndType(
                user.getId(),
                SecurityTokenType.EMAIL_VERIFICATION,
                Instant.now()
        );

        String rawToken = generateRawToken();
        String tokenHash = HashUtil.sha256(rawToken);
        Instant expiresAt = Instant.now().plusSeconds(emailVerificationExpirationSeconds);

        SecurityToken securityToken = SecurityToken.builder()
                .user(user)
                .tokenType(SecurityTokenType.EMAIL_VERIFICATION)
                .tokenHash(tokenHash)
                .expiresAt(expiresAt)
                .build();

        securityTokenRepository.save(securityToken);
        log.info("Created email verification token for user {}", user.getId());
        return rawToken;
    }

    @Transactional
    public void verifyEmail(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new BadRequestException("Verification token is required", "INVALID_TOKEN");
        }

        String tokenHash = HashUtil.sha256(rawToken.trim());
        SecurityToken token = securityTokenRepository
                .findByTokenHashAndTokenType(tokenHash, SecurityTokenType.EMAIL_VERIFICATION)
                .orElseThrow(() -> new BadRequestException("Invalid or non-existent verification token", "INVALID_TOKEN"));

        if (token.isUsed()) {
            throw new BadRequestException("Verification token has already been used", "TOKEN_ALREADY_USED");
        }

        if (token.isExpired()) {
            throw new BadRequestException("Verification token has expired", "TOKEN_EXPIRED");
        }

        token.setUsedAt(Instant.now());
        securityTokenRepository.save(token);

        User user = token.getUser();
        user.setEmailVerified(true);
        userRepository.save(user);

        log.info("Email verified successfully for user {}", user.getId());
    }

    @Transactional
    public Optional<String> createPasswordResetToken(String email) {
        String normalizedEmail = UserService.normalizeEmail(email);
        Optional<User> userOptional = userRepository.findByEmail(normalizedEmail);

        if (userOptional.isEmpty()) {
            log.info("Password reset requested for non-existent email: {}", normalizedEmail);
            return Optional.empty();
        }

        User user = userOptional.get();

        securityTokenRepository.invalidateAllActiveByUserIdAndType(
                user.getId(),
                SecurityTokenType.PASSWORD_RESET,
                Instant.now()
        );

        String rawToken = generateRawToken();
        String tokenHash = HashUtil.sha256(rawToken);
        Instant expiresAt = Instant.now().plusSeconds(passwordResetExpirationSeconds);

        SecurityToken securityToken = SecurityToken.builder()
                .user(user)
                .tokenType(SecurityTokenType.PASSWORD_RESET)
                .tokenHash(tokenHash)
                .expiresAt(expiresAt)
                .build();

        securityTokenRepository.save(securityToken);
        log.info("Created password reset token for user {}", user.getId());
        return Optional.of(rawToken);
    }

    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new BadRequestException("Reset token is required", "INVALID_TOKEN");
        }

        String tokenHash = HashUtil.sha256(rawToken.trim());
        SecurityToken token = securityTokenRepository
                .findByTokenHashAndTokenType(tokenHash, SecurityTokenType.PASSWORD_RESET)
                .orElseThrow(() -> new BadRequestException("Invalid or non-existent reset token", "INVALID_TOKEN"));

        if (token.isUsed()) {
            throw new BadRequestException("Password reset token has already been used", "TOKEN_ALREADY_USED");
        }

        if (token.isExpired()) {
            throw new BadRequestException("Password reset token has expired", "TOKEN_EXPIRED");
        }

        token.setUsedAt(Instant.now());
        securityTokenRepository.save(token);

        User user = token.getUser();

        UserAuthIdentity identity = identityRepository.findByUserIdAndProvider(user.getId(), AuthProvider.LOCAL)
                .orElseGet(() -> UserAuthIdentity.builder()
                        .user(user)
                        .provider(AuthProvider.LOCAL)
                        .providerUserId(user.getEmail())
                        .build());

        identity.setPasswordHash(passwordEncoder.encode(newPassword));
        identityRepository.save(identity);

        // Security rule: Terminate all active sessions upon password reset
        refreshTokenService.revokeAllUserTokens(user.getId());
        log.info("Password reset successfully and active sessions revoked for user {}", user.getId());
    }

    private String generateRawToken() {
        return UUID.randomUUID().toString().replace("-", "") +
                UUID.randomUUID().toString().replace("-", "");
    }
}

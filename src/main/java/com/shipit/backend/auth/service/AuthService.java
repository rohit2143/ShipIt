package com.shipit.backend.auth.service;

import com.shipit.backend.auth.dto.*;
import com.shipit.backend.auth.entity.AuthProvider;
import com.shipit.backend.auth.entity.UserAuthIdentity;
import com.shipit.backend.auth.repository.UserAuthIdentityRepository;
import com.shipit.backend.common.exception.ConflictException;
import com.shipit.backend.common.exception.ForbiddenException;
import com.shipit.backend.common.exception.UnauthorizedException;
import com.shipit.backend.config.JwtProperties;
import com.shipit.backend.user.dto.UserResponse;
import com.shipit.backend.user.entity.Role;
import com.shipit.backend.user.entity.User;
import com.shipit.backend.user.entity.UserStatus;
import com.shipit.backend.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserService userService;
    private final UserAuthIdentityRepository identityRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final JwtProperties jwtProperties;

    @Transactional
    public AuthResponse register(RegisterRequest request, String userAgent, String ipAddress) {
        String normalizedEmail = UserService.normalizeEmail(request.getEmail());

        if (userService.existsByEmail(normalizedEmail)) {
            throw new ConflictException("An account with this email already exists");
        }

        User user = userService.createUser(
                request.getName(),
                normalizedEmail,
                Role.ROLE_USER,
                UserStatus.ACTIVE,
                false
        );

        UserAuthIdentity identity = UserAuthIdentity.builder()
                .user(user)
                .provider(AuthProvider.LOCAL)
                .providerUserId(normalizedEmail)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .build();
        identityRepository.save(identity);

        String accessToken = jwtService.generateAccessToken(user);
        RefreshTokenService.RotatedToken refreshToken =
                refreshTokenService.createRefreshToken(user, userAgent, ipAddress);

        TokenResponse tokenResponse = TokenResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken.rawToken())
                .tokenType("Bearer")
                .expiresIn(jwtProperties.getAccessTokenExpirationSeconds())
                .build();

        log.info("User registered successfully with id: {}", user.getId());
        return AuthResponse.builder()
                .user(UserResponse.fromUser(user))
                .tokens(tokenResponse)
                .build();
    }

    @Transactional
    public AuthResponse login(LoginRequest request, String userAgent, String ipAddress) {
        String normalizedEmail = UserService.normalizeEmail(request.getEmail());

        UserAuthIdentity identity = identityRepository.findByProviderAndProviderUserId(AuthProvider.LOCAL, normalizedEmail)
                .orElseThrow(() -> new UnauthorizedException("Invalid email or password"));

        if (!passwordEncoder.matches(request.getPassword(), identity.getPasswordHash())) {
            throw new UnauthorizedException("Invalid email or password");
        }

        User user = identity.getUser();
        if (user.getStatus() == UserStatus.SUSPENDED) {
            throw new ForbiddenException("Account has been suspended");
        }

        String accessToken = jwtService.generateAccessToken(user);
        RefreshTokenService.RotatedToken refreshToken =
                refreshTokenService.createRefreshToken(user, userAgent, ipAddress);

        TokenResponse tokenResponse = TokenResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken.rawToken())
                .tokenType("Bearer")
                .expiresIn(jwtProperties.getAccessTokenExpirationSeconds())
                .build();

        log.info("User logged in successfully with id: {}", user.getId());
        return AuthResponse.builder()
                .user(UserResponse.fromUser(user))
                .tokens(tokenResponse)
                .build();
    }

    @Transactional
    public TokenResponse refreshToken(RefreshTokenRequest request, String userAgent, String ipAddress) {
        RefreshTokenService.RotatedToken rotated =
                refreshTokenService.rotateRefreshToken(request.getRefreshToken(), userAgent, ipAddress);

        User user = rotated.entity().getUser();
        String accessToken = jwtService.generateAccessToken(user);

        return TokenResponse.builder()
                .accessToken(accessToken)
                .refreshToken(rotated.rawToken())
                .tokenType("Bearer")
                .expiresIn(jwtProperties.getAccessTokenExpirationSeconds())
                .build();
    }

    @Transactional
    public void logout(String refreshToken) {
        refreshTokenService.revokeToken(refreshToken);
    }
}

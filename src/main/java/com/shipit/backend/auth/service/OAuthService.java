package com.shipit.backend.auth.service;

import com.shipit.backend.auth.dto.AuthResponse;
import com.shipit.backend.auth.dto.OAuthLoginRequest;
import com.shipit.backend.auth.dto.TokenResponse;
import com.shipit.backend.auth.entity.AuthProvider;
import com.shipit.backend.auth.entity.UserAuthIdentity;
import com.shipit.backend.auth.repository.UserAuthIdentityRepository;
import com.shipit.backend.common.exception.BadRequestException;
import com.shipit.backend.common.exception.ConflictException;
import com.shipit.backend.common.exception.ForbiddenException;
import com.shipit.backend.config.JwtProperties;
import com.shipit.backend.user.dto.IdentityResponse;
import com.shipit.backend.user.dto.UserResponse;
import com.shipit.backend.user.entity.Role;
import com.shipit.backend.user.entity.User;
import com.shipit.backend.user.entity.UserStatus;
import com.shipit.backend.user.repository.UserRepository;
import com.shipit.backend.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OAuthService {

    private final GitHubOAuthClient gitHubOAuthClient;
    private final UserAuthIdentityRepository identityRepository;
    private final UserRepository userRepository;
    private final UserService userService;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final JwtProperties jwtProperties;

    @Transactional
    public AuthResponse authenticateWithGitHub(OAuthLoginRequest request, String userAgent, String ipAddress) {
        String accessToken = gitHubOAuthClient.exchangeCodeForAccessToken(request.getCode(), request.getRedirectUri());
        GitHubUserProfile profile = gitHubOAuthClient.fetchUserProfile(accessToken);

        // 1. Check if identity exists by (GITHUB, githubId)
        Optional<UserAuthIdentity> existingIdentity =
                identityRepository.findByProviderAndProviderUserId(AuthProvider.GITHUB, profile.id());

        User user;
        if (existingIdentity.isPresent()) {
            user = existingIdentity.get().getUser();
            if (user.getStatus() == UserStatus.SUSPENDED) {
                throw new ForbiddenException("Account has been suspended");
            }
            log.info("Existing GitHub user logged in with id: {}", user.getId());
        } else {
            // 2. Identity does not exist. Check if user with that email already exists in ShipIt
            Optional<User> existingUserByEmail = userRepository.findByEmail(profile.email());
            if (existingUserByEmail.isPresent()) {
                // SECURITY GUARD: Never auto-merge by email
                log.warn("GitHub login attempt for existing email {} without linked GitHub identity", profile.email());
                throw new ConflictException(
                        "An account with this email already exists. Please log in with your email and password to link your GitHub account.",
                        "ACCOUNT_EXISTS_LINK_REQUIRED"
                );
            }

            // 3. Register new user + GITHUB identity
            user = userService.createUser(
                    profile.name(),
                    profile.email(),
                    Role.ROLE_USER,
                    UserStatus.ACTIVE,
                    true // GitHub emails are verified
            );

            UserAuthIdentity newIdentity = UserAuthIdentity.builder()
                    .user(user)
                    .provider(AuthProvider.GITHUB)
                    .providerUserId(profile.id())
                    .passwordHash(null)
                    .build();
            identityRepository.save(newIdentity);

            log.info("Registered new user {} with GitHub identity {}", user.getId(), profile.id());
        }

        // Issue JWT & refresh token
        String jwtToken = jwtService.generateAccessToken(user);
        RefreshTokenService.RotatedToken refreshToken =
                refreshTokenService.createRefreshToken(user, userAgent, ipAddress);

        TokenResponse tokenResponse = TokenResponse.builder()
                .accessToken(jwtToken)
                .refreshToken(refreshToken.rawToken())
                .tokenType("Bearer")
                .expiresIn(jwtProperties.getAccessTokenExpirationSeconds())
                .build();

        return AuthResponse.builder()
                .user(UserResponse.fromUser(user))
                .tokens(tokenResponse)
                .build();
    }

    @Transactional
    public IdentityResponse linkGitHubAccount(UUID userId, String code, String redirectUri) {
        User user = userService.getById(userId);

        String accessToken = gitHubOAuthClient.exchangeCodeForAccessToken(code, redirectUri);
        GitHubUserProfile profile = gitHubOAuthClient.fetchUserProfile(accessToken);

        // Check if this GitHub account is already linked to anyone
        Optional<UserAuthIdentity> existingIdentity =
                identityRepository.findByProviderAndProviderUserId(AuthProvider.GITHUB, profile.id());

        if (existingIdentity.isPresent()) {
            if (existingIdentity.get().getUser().getId().equals(userId)) {
                log.info("GitHub account {} is already linked to user {}", profile.id(), userId);
                return IdentityResponse.fromEntity(existingIdentity.get());
            } else {
                throw new ConflictException(
                        "This GitHub account is already linked to another ShipIt user.",
                        "GITHUB_ALREADY_LINKED"
                );
            }
        }

        // Check if current user already has another GitHub identity linked
        if (identityRepository.existsByUserIdAndProvider(userId, AuthProvider.GITHUB)) {
            throw new ConflictException(
                    "You already have a GitHub account linked. Unlink it first to link a different one.",
                    "PROVIDER_ALREADY_LINKED"
            );
        }

        UserAuthIdentity newIdentity = UserAuthIdentity.builder()
                .user(user)
                .provider(AuthProvider.GITHUB)
                .providerUserId(profile.id())
                .passwordHash(null)
                .build();

        UserAuthIdentity saved = identityRepository.save(newIdentity);
        log.info("Successfully linked GitHub account {} to user {}", profile.id(), userId);
        return IdentityResponse.fromEntity(saved);
    }

    @Transactional
    public void unlinkIdentity(UUID userId, AuthProvider provider) {
        long identityCount = identityRepository.countByUserId(userId);
        if (identityCount <= 1) {
            throw new BadRequestException(
                    "Cannot unlink your sole authentication method. Please add another identity or set a password first.",
                    "CANNOT_UNLINK_SOLE_IDENTITY"
            );
        }

        UserAuthIdentity identity = identityRepository.findByUserIdAndProvider(userId, provider)
                .orElseThrow(() -> new BadRequestException(
                        "No linked identity found for provider: " + provider,
                        "IDENTITY_NOT_FOUND"
                ));

        identityRepository.delete(identity);
        log.info("Successfully unlinked provider {} for user {}", provider, userId);
    }

    @Transactional(readOnly = true)
    public List<IdentityResponse> getUserIdentities(UUID userId) {
        return identityRepository.findAllByUserId(userId).stream()
                .map(IdentityResponse::fromEntity)
                .toList();
    }
}

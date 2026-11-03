package com.shipit.backend.auth.service;

import com.shipit.backend.auth.dto.AuthResponse;
import com.shipit.backend.auth.dto.OAuthLoginRequest;
import com.shipit.backend.auth.entity.AuthProvider;
import com.shipit.backend.auth.entity.RefreshToken;
import com.shipit.backend.auth.entity.UserAuthIdentity;
import com.shipit.backend.auth.repository.UserAuthIdentityRepository;
import com.shipit.backend.common.exception.BadRequestException;
import com.shipit.backend.common.exception.ConflictException;
import com.shipit.backend.config.JwtProperties;
import com.shipit.backend.user.dto.IdentityResponse;
import com.shipit.backend.user.entity.Role;
import com.shipit.backend.user.entity.User;
import com.shipit.backend.user.entity.UserStatus;
import com.shipit.backend.user.repository.UserRepository;
import com.shipit.backend.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OAuthServiceTest {

    @Mock
    private GitHubOAuthClient gitHubOAuthClient;

    @Mock
    private UserAuthIdentityRepository identityRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserService userService;

    @Mock
    private JwtService jwtService;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private JwtProperties jwtProperties;

    @InjectMocks
    private OAuthService oAuthService;

    private User sampleUser;
    private GitHubUserProfile sampleProfile;

    @BeforeEach
    void setUp() {
        sampleUser = User.builder()
                .id(UUID.randomUUID())
                .name("Octo Dev")
                .email("octo@github.com")
                .role(Role.ROLE_USER)
                .status(UserStatus.ACTIVE)
                .emailVerified(true)
                .build();

        sampleProfile = new GitHubUserProfile("987654", "Octo Dev", "octo@github.com", "https://avatar.url");
    }

    @Test
    @DisplayName("Should register new user and link GitHub identity when first logging in via GitHub")
    void shouldRegisterNewGitHubUser() {
        OAuthLoginRequest request = OAuthLoginRequest.builder().code("oauth-code-123").build();

        when(gitHubOAuthClient.exchangeCodeForAccessToken("oauth-code-123", null)).thenReturn("gh_access_token");
        when(gitHubOAuthClient.fetchUserProfile("gh_access_token")).thenReturn(sampleProfile);
        when(identityRepository.findByProviderAndProviderUserId(AuthProvider.GITHUB, "987654"))
                .thenReturn(Optional.empty());
        when(userRepository.findByEmail("octo@github.com")).thenReturn(Optional.empty());
        when(userService.createUser("Octo Dev", "octo@github.com", Role.ROLE_USER, UserStatus.ACTIVE, true))
                .thenReturn(sampleUser);
        when(jwtService.generateAccessToken(sampleUser)).thenReturn("jwt.token.here");
        when(jwtProperties.getAccessTokenExpirationSeconds()).thenReturn(900L);

        RefreshToken refreshTokenEntity = RefreshToken.builder().build();
        when(refreshTokenService.createRefreshToken(eq(sampleUser), any(), any()))
                .thenReturn(new RefreshTokenService.RotatedToken("refresh.token.here", refreshTokenEntity));

        AuthResponse response = oAuthService.authenticateWithGitHub(request, "Agent/1.0", "127.0.0.1");

        assertThat(response).isNotNull();
        assertThat(response.getUser().getEmail()).isEqualTo("octo@github.com");
        assertThat(response.getTokens().getAccessToken()).isEqualTo("jwt.token.here");

        verify(identityRepository).save(any(UserAuthIdentity.class));
    }

    @Test
    @DisplayName("Should authenticate existing GitHub identity directly")
    void shouldAuthenticateExistingGitHubUser() {
        OAuthLoginRequest request = OAuthLoginRequest.builder().code("oauth-code-123").build();

        UserAuthIdentity existingIdentity = UserAuthIdentity.builder()
                .user(sampleUser)
                .provider(AuthProvider.GITHUB)
                .providerUserId("987654")
                .build();

        when(gitHubOAuthClient.exchangeCodeForAccessToken("oauth-code-123", null)).thenReturn("gh_access_token");
        when(gitHubOAuthClient.fetchUserProfile("gh_access_token")).thenReturn(sampleProfile);
        when(identityRepository.findByProviderAndProviderUserId(AuthProvider.GITHUB, "987654"))
                .thenReturn(Optional.of(existingIdentity));
        when(jwtService.generateAccessToken(sampleUser)).thenReturn("jwt.token.here");
        when(jwtProperties.getAccessTokenExpirationSeconds()).thenReturn(900L);

        RefreshToken refreshTokenEntity = RefreshToken.builder().build();
        when(refreshTokenService.createRefreshToken(eq(sampleUser), any(), any()))
                .thenReturn(new RefreshTokenService.RotatedToken("refresh.token.here", refreshTokenEntity));

        AuthResponse response = oAuthService.authenticateWithGitHub(request, "Agent/1.0", "127.0.0.1");

        assertThat(response.getUser().getEmail()).isEqualTo("octo@github.com");
        verifyNoInteractions(userService);
    }

    @Test
    @DisplayName("Should reject GitHub login with conflict when email already exists locally without linked GitHub identity")
    void shouldRejectAutoMergeWhenEmailExistsLocally() {
        OAuthLoginRequest request = OAuthLoginRequest.builder().code("oauth-code-123").build();

        when(gitHubOAuthClient.exchangeCodeForAccessToken("oauth-code-123", null)).thenReturn("gh_access_token");
        when(gitHubOAuthClient.fetchUserProfile("gh_access_token")).thenReturn(sampleProfile);
        when(identityRepository.findByProviderAndProviderUserId(AuthProvider.GITHUB, "987654"))
                .thenReturn(Optional.empty());
        when(userRepository.findByEmail("octo@github.com")).thenReturn(Optional.of(sampleUser));

        assertThatThrownBy(() -> oAuthService.authenticateWithGitHub(request, "Agent/1.0", "127.0.0.1"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("An account with this email already exists");

        verify(identityRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should successfully link GitHub account to authenticated user")
    void shouldLinkGitHubAccount() {
        UUID userId = sampleUser.getId();
        when(userService.getById(userId)).thenReturn(sampleUser);
        when(gitHubOAuthClient.exchangeCodeForAccessToken("code-link", "http://callback")).thenReturn("token");
        when(gitHubOAuthClient.fetchUserProfile("token")).thenReturn(sampleProfile);
        when(identityRepository.findByProviderAndProviderUserId(AuthProvider.GITHUB, "987654"))
                .thenReturn(Optional.empty());
        when(identityRepository.existsByUserIdAndProvider(userId, AuthProvider.GITHUB)).thenReturn(false);

        UserAuthIdentity savedIdentity = UserAuthIdentity.builder()
                .id(UUID.randomUUID())
                .user(sampleUser)
                .provider(AuthProvider.GITHUB)
                .providerUserId("987654")
                .build();
        when(identityRepository.save(any(UserAuthIdentity.class))).thenReturn(savedIdentity);

        IdentityResponse response = oAuthService.linkGitHubAccount(userId, "code-link", "http://callback");

        assertThat(response).isNotNull();
        assertThat(response.getProvider()).isEqualTo(AuthProvider.GITHUB);
        assertThat(response.getProviderUserId()).isEqualTo("987654");
    }

    @Test
    @DisplayName("Should reject linking when GitHub account is already bound to another user")
    void shouldRejectLinkingWhenGitHubAccountBoundToAnother() {
        UUID currentUserId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();

        User otherUser = User.builder().id(otherUserId).email("other@shipit.dev").build();
        UserAuthIdentity existingOtherIdentity = UserAuthIdentity.builder()
                .user(otherUser)
                .provider(AuthProvider.GITHUB)
                .providerUserId("987654")
                .build();

        when(userService.getById(currentUserId)).thenReturn(sampleUser);
        when(gitHubOAuthClient.exchangeCodeForAccessToken("code-link", null)).thenReturn("token");
        when(gitHubOAuthClient.fetchUserProfile("token")).thenReturn(sampleProfile);
        when(identityRepository.findByProviderAndProviderUserId(AuthProvider.GITHUB, "987654"))
                .thenReturn(Optional.of(existingOtherIdentity));

        assertThatThrownBy(() -> oAuthService.linkGitHubAccount(currentUserId, "code-link", null))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already linked to another ShipIt user");
    }

    @Test
    @DisplayName("Should prevent unlinking sole authentication identity (lockout protection)")
    void shouldPreventUnlinkingSoleIdentity() {
        UUID userId = sampleUser.getId();
        when(identityRepository.countByUserId(userId)).thenReturn(1L);

        assertThatThrownBy(() -> oAuthService.unlinkIdentity(userId, AuthProvider.GITHUB))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Cannot unlink your sole authentication method");

        verify(identityRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Should successfully unlink identity when multiple identities exist")
    void shouldUnlinkIdentityWhenMultipleExist() {
        UUID userId = sampleUser.getId();
        UserAuthIdentity identity = UserAuthIdentity.builder()
                .user(sampleUser)
                .provider(AuthProvider.GITHUB)
                .build();

        when(identityRepository.countByUserId(userId)).thenReturn(2L);
        when(identityRepository.findByUserIdAndProvider(userId, AuthProvider.GITHUB))
                .thenReturn(Optional.of(identity));

        oAuthService.unlinkIdentity(userId, AuthProvider.GITHUB);

        verify(identityRepository).delete(identity);
    }
}

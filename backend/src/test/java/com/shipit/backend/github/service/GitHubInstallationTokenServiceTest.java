package com.shipit.backend.github.service;

import com.shipit.backend.config.GitHubAppProperties;
import com.shipit.backend.github.client.GitHubAppClient;
import com.shipit.backend.github.dto.GitHubInstallationTokenResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GitHubInstallationTokenServiceTest {

    @Mock
    private GitHubAppClient appClient;

    private GitHubAppProperties properties;
    private GitHubInstallationTokenService tokenService;

    @BeforeEach
    void setUp() {
        properties = new GitHubAppProperties();
        properties.setTokenBufferSeconds(300); // 5 minute safety buffer

        tokenService = new GitHubInstallationTokenService(appClient, properties);
    }

    @Test
    @DisplayName("Should fetch fresh token from GitHub on cache miss and cache it")
    void shouldFetchFreshTokenOnCacheMiss() {
        Long installationId = 12345L;
        Instant expiresAt = Instant.now().plusSeconds(3600);
        GitHubInstallationTokenResponse apiResponse = GitHubInstallationTokenResponse.builder()
                .token("ghs_first_token")
                .expiresAt(expiresAt)
                .permissions(Map.of("contents", "read"))
                .repositorySelection("all")
                .build();

        when(appClient.createInstallationAccessToken(installationId)).thenReturn(apiResponse);

        String token = tokenService.getInstallationToken(installationId);

        assertThat(token).isEqualTo("ghs_first_token");
        assertThat(tokenService.hasValidCachedToken(installationId)).isTrue();
        verify(appClient, times(1)).createInstallationAccessToken(installationId);
    }

    @Test
    @DisplayName("Should return cached token on cache hit without calling GitHub API")
    void shouldReturnCachedTokenOnCacheHit() {
        Long installationId = 12345L;
        Instant expiresAt = Instant.now().plusSeconds(3600);
        GitHubInstallationTokenResponse apiResponse = GitHubInstallationTokenResponse.builder()
                .token("ghs_cached_token")
                .expiresAt(expiresAt)
                .permissions(Map.of("contents", "read"))
                .repositorySelection("all")
                .build();

        when(appClient.createInstallationAccessToken(installationId)).thenReturn(apiResponse);

        // First call - cache miss
        String first = tokenService.getInstallationToken(installationId);
        // Second call - cache hit
        String second = tokenService.getInstallationToken(installationId);

        assertThat(first).isEqualTo("ghs_cached_token");
        assertThat(second).isEqualTo("ghs_cached_token");
        verify(appClient, times(1)).createInstallationAccessToken(installationId);
    }

    @Test
    @DisplayName("Should refresh token when cached token is within safety expiration buffer")
    void shouldRefreshTokenWhenWithinBuffer() {
        Long installationId = 12345L;
        // Token expires in 2 minutes, which is within the 5-minute buffer
        Instant nearExpiry = Instant.now().plusSeconds(120);
        GitHubInstallationTokenResponse nearExpiryResponse = GitHubInstallationTokenResponse.builder()
                .token("ghs_expiring_soon")
                .expiresAt(nearExpiry)
                .build();

        Instant freshExpiry = Instant.now().plusSeconds(3600);
        GitHubInstallationTokenResponse freshResponse = GitHubInstallationTokenResponse.builder()
                .token("ghs_fresh_token")
                .expiresAt(freshExpiry)
                .build();

        when(appClient.createInstallationAccessToken(installationId))
                .thenReturn(nearExpiryResponse)
                .thenReturn(freshResponse);

        // Initial fetch
        tokenService.getInstallationToken(installationId);

        // Next fetch detects token is within buffer window and refreshes
        String refreshed = tokenService.getInstallationToken(installationId);

        assertThat(refreshed).isEqualTo("ghs_fresh_token");
        verify(appClient, times(2)).createInstallationAccessToken(installationId);
    }

    @Test
    @DisplayName("Should evict token from cache upon evictToken call")
    void shouldEvictTokenFromCache() {
        Long installationId = 12345L;
        Instant expiresAt = Instant.now().plusSeconds(3600);
        GitHubInstallationTokenResponse apiResponse = GitHubInstallationTokenResponse.builder()
                .token("ghs_evict_me")
                .expiresAt(expiresAt)
                .build();

        when(appClient.createInstallationAccessToken(installationId)).thenReturn(apiResponse);

        tokenService.getInstallationToken(installationId);
        assertThat(tokenService.hasValidCachedToken(installationId)).isTrue();

        tokenService.evictToken(installationId);
        assertThat(tokenService.hasValidCachedToken(installationId)).isFalse();
    }

    @Test
    @DisplayName("Should clear all cached tokens upon clearCache call")
    void shouldClearAllTokens() {
        Long id1 = 111L;
        Long id2 = 222L;
        Instant expiresAt = Instant.now().plusSeconds(3600);

        when(appClient.createInstallationAccessToken(id1))
                .thenReturn(GitHubInstallationTokenResponse.builder().token("t1").expiresAt(expiresAt).build());
        when(appClient.createInstallationAccessToken(id2))
                .thenReturn(GitHubInstallationTokenResponse.builder().token("t2").expiresAt(expiresAt).build());

        tokenService.getInstallationToken(id1);
        tokenService.getInstallationToken(id2);

        assertThat(tokenService.hasValidCachedToken(id1)).isTrue();
        assertThat(tokenService.hasValidCachedToken(id2)).isTrue();

        tokenService.clearCache();

        assertThat(tokenService.hasValidCachedToken(id1)).isFalse();
        assertThat(tokenService.hasValidCachedToken(id2)).isFalse();
    }
}

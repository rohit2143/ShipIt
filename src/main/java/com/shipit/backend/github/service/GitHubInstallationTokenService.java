package com.shipit.backend.github.service;

import com.shipit.backend.config.GitHubAppProperties;
import com.shipit.backend.github.client.GitHubAppClient;
import com.shipit.backend.github.dto.GitHubInstallationTokenResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class GitHubInstallationTokenService {

    public record CachedToken(String token, Instant expiresAt) {
        public boolean isValid(long bufferSeconds) {
            return expiresAt != null && Instant.now().plusSeconds(bufferSeconds).isBefore(expiresAt);
        }
    }

    private final GitHubAppClient appClient;
    private final GitHubAppProperties properties;
    private final Map<Long, CachedToken> tokenCache = new ConcurrentHashMap<>();

    public GitHubInstallationTokenService(GitHubAppClient appClient, GitHubAppProperties properties) {
        this.appClient = appClient;
        this.properties = properties;
    }

    /**
     * Retrieves a valid short-lived installation access token for the given installation ID.
     * Returns from in-memory cache if valid; otherwise generates a new token from GitHub and caches it.
     * Strictly ephemeral: tokens are NEVER persisted to the database.
     */
    public String getInstallationToken(Long installationId) {
        long bufferSeconds = properties.getTokenBufferSeconds();

        CachedToken cached = tokenCache.get(installationId);
        if (cached != null && cached.isValid(bufferSeconds)) {
            log.debug("Returning cached installation token for installation {}", installationId);
            return cached.token();
        }

        log.info("Requesting fresh installation access token from GitHub for installation {}", installationId);
        GitHubInstallationTokenResponse response = appClient.createInstallationAccessToken(installationId);

        CachedToken newToken = new CachedToken(response.getToken(), response.getExpiresAt());
        tokenCache.put(installationId, newToken);

        log.debug("Cached new installation token for installation {}, expires at {}",
                installationId, response.getExpiresAt());
        return newToken.token();
    }

    /**
     * Evicts the cached installation token for the given installation ID.
     */
    public void evictToken(Long installationId) {
        if (installationId != null) {
            tokenCache.remove(installationId);
            log.debug("Evicted cached installation token for installation {}", installationId);
        }
    }

    /**
     * Clears all cached tokens.
     */
    public void clearCache() {
        tokenCache.clear();
        log.debug("Cleared all cached installation tokens");
    }

    /**
     * Checks if a valid cached token exists without requesting a new one.
     */
    public boolean hasValidCachedToken(Long installationId) {
        CachedToken cached = tokenCache.get(installationId);
        return cached != null && cached.isValid(properties.getTokenBufferSeconds());
    }
}

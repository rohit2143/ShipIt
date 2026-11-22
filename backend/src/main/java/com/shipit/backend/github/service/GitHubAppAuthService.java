package com.shipit.backend.github.service;

import com.shipit.backend.common.exception.AppException;
import com.shipit.backend.config.GitHubAppProperties;
import io.jsonwebtoken.Jwts;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.security.interfaces.RSAPrivateKey;
import java.time.Instant;
import java.util.Date;

@Slf4j
@Service
public class GitHubAppAuthService {

    private final GitHubAppProperties properties;
    private final GitHubAppKeyLoader keyLoader;

    public GitHubAppAuthService(GitHubAppProperties properties, GitHubAppKeyLoader keyLoader) {
        this.properties = properties;
        this.keyLoader = keyLoader;
    }

    public String generateAppJwt() {
        String appId = properties.getAppId();
        if (appId == null || appId.isBlank()) {
            throw new AppException(
                    "GitHub App ID is not configured (shipit.github.app.app-id)",
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "GITHUB_APP_ID_NOT_CONFIGURED"
            );
        }

        RSAPrivateKey privateKey = keyLoader.loadPrivateKey();

        Instant now = Instant.now();
        // GitHub recommends backdating iat by 60 seconds to mitigate clock drift
        Instant issuedAt = now.minusSeconds(60);
        // GitHub allows maximum 10-minute expiration for App JWTs; 9 minutes provides safety
        Instant expiresAt = now.plusSeconds(9 * 60);

        return Jwts.builder()
                .header()
                .type("JWT")
                .and()
                .issuer(appId.trim())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(privateKey, Jwts.SIG.RS256)
                .compact();
    }
}

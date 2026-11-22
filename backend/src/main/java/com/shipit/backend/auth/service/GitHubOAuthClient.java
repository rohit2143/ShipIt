package com.shipit.backend.auth.service;

import com.shipit.backend.common.exception.BadRequestException;
import com.shipit.backend.config.OAuthProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class GitHubOAuthClient {

    private final OAuthProperties oAuthProperties;
    private final RestClient restClient;

    public GitHubOAuthClient(OAuthProperties oAuthProperties) {
        this.oAuthProperties = oAuthProperties;
        this.restClient = RestClient.builder().build();
    }

    public String exchangeCodeForAccessToken(String code, String redirectUri) {
        OAuthProperties.GitHub githubConfig = oAuthProperties.getGithub();

        Map<String, String> payload = Map.of(
                "client_id", githubConfig.getClientId(),
                "client_secret", githubConfig.getClientSecret(),
                "code", code,
                "redirect_uri", redirectUri != null && !redirectUri.isBlank()
                        ? redirectUri : githubConfig.getRedirectUri()
        );

        try {
            Map<String, Object> response = restClient.post()
                    .uri("https://github.com/login/oauth/access_token")
                    .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});

            if (response == null || !response.containsKey("access_token")) {
                String error = response != null && response.containsKey("error_description")
                        ? (String) response.get("error_description")
                        : "Failed to exchange GitHub authorization code";
                throw new BadRequestException(error, "OAUTH_EXCHANGE_FAILED");
            }

            return (String) response.get("access_token");
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error exchanging GitHub OAuth code: {}", e.getMessage());
            throw new BadRequestException("Failed to exchange GitHub authorization code: " + e.getMessage(),
                    "OAUTH_EXCHANGE_FAILED");
        }
    }

    public GitHubUserProfile fetchUserProfile(String accessToken) {
        try {
            Map<String, Object> userResponse = restClient.get()
                    .uri("https://api.github.com/user")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .header(HttpHeaders.ACCEPT, "application/vnd.github+json")
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});

            if (userResponse == null || !userResponse.containsKey("id")) {
                throw new BadRequestException("Invalid GitHub user response", "OAUTH_PROFILE_FAILED");
            }

            String githubId = String.valueOf(userResponse.get("id"));
            String name = (String) userResponse.get("name");
            if (name == null || name.isBlank()) {
                name = (String) userResponse.get("login");
            }
            String avatarUrl = (String) userResponse.get("avatar_url");
            String email = (String) userResponse.get("email");

            // If public email is null, fetch from user/emails endpoint
            if (email == null || email.isBlank()) {
                email = fetchPrimaryEmail(accessToken);
            }

            if (email == null || email.isBlank()) {
                throw new BadRequestException("No verified email address found on GitHub account",
                        "OAUTH_EMAIL_MISSING");
            }

            return new GitHubUserProfile(githubId, name, email.trim().toLowerCase(), avatarUrl);
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error fetching GitHub user profile: {}", e.getMessage());
            throw new BadRequestException("Failed to fetch GitHub user profile: " + e.getMessage(),
                    "OAUTH_PROFILE_FAILED");
        }
    }

    private String fetchPrimaryEmail(String accessToken) {
        try {
            List<Map<String, Object>> emails = restClient.get()
                    .uri("https://api.github.com/user/emails")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .header(HttpHeaders.ACCEPT, "application/vnd.github+json")
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});

            if (emails != null) {
                for (Map<String, Object> emailObj : emails) {
                    Boolean primary = (Boolean) emailObj.get("primary");
                    Boolean verified = (Boolean) emailObj.get("verified");
                    if (Boolean.TRUE.equals(primary) && Boolean.TRUE.equals(verified)) {
                        return (String) emailObj.get("email");
                    }
                }
                // Fallback to any verified email
                for (Map<String, Object> emailObj : emails) {
                    Boolean verified = (Boolean) emailObj.get("verified");
                    if (Boolean.TRUE.equals(verified)) {
                        return (String) emailObj.get("email");
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Could not fetch GitHub user emails: {}", e.getMessage());
        }
        return null;
    }
}

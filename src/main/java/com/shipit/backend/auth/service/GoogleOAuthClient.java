package com.shipit.backend.auth.service;

import com.shipit.backend.common.exception.BadRequestException;
import com.shipit.backend.config.OAuthProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Slf4j
@Component
public class GoogleOAuthClient {

    private final OAuthProperties oAuthProperties;
    private final RestClient restClient;

    public GoogleOAuthClient(OAuthProperties oAuthProperties) {
        this.oAuthProperties = oAuthProperties;
        this.restClient = RestClient.builder().build();
    }

    public String exchangeCodeForAccessToken(String code, String redirectUri) {
        OAuthProperties.Google googleConfig = oAuthProperties.getGoogle();

        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("client_id", googleConfig.getClientId());
        formData.add("client_secret", googleConfig.getClientSecret());
        formData.add("code", code);
        formData.add("grant_type", "authorization_code");
        formData.add("redirect_uri", redirectUri != null && !redirectUri.isBlank()
                ? redirectUri : googleConfig.getRedirectUri());

        try {
            Map<String, Object> response = restClient.post()
                    .uri("https://oauth2.googleapis.com/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(formData)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});

            if (response == null || !response.containsKey("access_token")) {
                String error = response != null && response.containsKey("error_description")
                        ? (String) response.get("error_description")
                        : "Failed to exchange Google authorization code";
                throw new BadRequestException(error, "OAUTH_EXCHANGE_FAILED");
            }

            return (String) response.get("access_token");
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error exchanging Google OAuth code: {}", e.getMessage());
            throw new BadRequestException("Failed to exchange Google authorization code: " + e.getMessage(),
                    "OAUTH_EXCHANGE_FAILED");
        }
    }

    public GoogleUserProfile fetchUserProfile(String accessToken) {
        try {
            Map<String, Object> userResponse = restClient.get()
                    .uri("https://www.googleapis.com/oauth2/v3/userinfo")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});

            if (userResponse == null || !userResponse.containsKey("sub")) {
                throw new BadRequestException("Invalid Google user response", "OAUTH_PROFILE_FAILED");
            }

            String googleId = String.valueOf(userResponse.get("sub"));
            String name = (String) userResponse.get("name");
            String email = (String) userResponse.get("email");
            String picture = (String) userResponse.get("picture");

            if (email == null || email.isBlank()) {
                throw new BadRequestException("No verified email address found on Google account",
                        "OAUTH_EMAIL_MISSING");
            }

            if (name == null || name.isBlank()) {
                name = email.split("@")[0];
            }

            return new GoogleUserProfile(googleId, name, email.trim().toLowerCase(), picture);
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error fetching Google user profile: {}", e.getMessage());
            throw new BadRequestException("Failed to fetch Google user profile: " + e.getMessage(),
                    "OAUTH_PROFILE_FAILED");
        }
    }
}

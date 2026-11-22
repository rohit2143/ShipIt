package com.shipit.backend.github.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class GitHubInstallationTokenResponse {

    private String token;

    @JsonProperty("expires_at")
    private Instant expiresAt;

    private Map<String, String> permissions;

    @JsonProperty("repository_selection")
    private String repositorySelection;
}

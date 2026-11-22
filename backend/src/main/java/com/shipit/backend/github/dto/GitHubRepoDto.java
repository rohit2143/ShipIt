package com.shipit.backend.github.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class GitHubRepoDto {

    private Long id;

    private String name;

    @JsonProperty("full_name")
    private String fullName;

    @JsonProperty("private")
    private boolean isPrivate;

    private GitHubAccountDto owner;

    @JsonProperty("html_url")
    private String htmlUrl;

    @JsonProperty("clone_url")
    private String cloneUrl;

    @JsonProperty("default_branch")
    private String defaultBranch;

    private String description;

    private Map<String, Boolean> permissions;
}

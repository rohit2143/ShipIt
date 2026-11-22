package com.shipit.backend.github.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class GitHubPushPayloadDto {

    private String ref;
    private String before;
    private String after;
    private Boolean created;
    private Boolean deleted;
    private Boolean forced;
    private GitHubPushRepositoryDto repository;
    private GitHubPushUserDto pusher;
    private GitHubPushUserDto sender;

    @JsonProperty("head_commit")
    private GitHubPushCommitDto headCommit;

    private List<GitHubPushCommitDto> commits;
    private GitHubPushInstallationDto installation;
}

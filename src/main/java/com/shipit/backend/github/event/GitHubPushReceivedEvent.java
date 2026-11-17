package com.shipit.backend.github.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
@AllArgsConstructor
public class GitHubPushReceivedEvent {

    private final UUID repositoryId;
    private final Long githubRepoId;
    private final String repoFullName;
    private final String cloneUrl;
    private final String defaultBranch;
    private final String branch;
    private final String commitSha;
    private final String commitMessage;
    private final String pusherName;
    private final String pusherEmail;
    private final Instant timestamp;
}

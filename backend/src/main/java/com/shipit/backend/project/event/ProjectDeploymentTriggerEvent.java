package com.shipit.backend.project.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
@AllArgsConstructor
public class ProjectDeploymentTriggerEvent {

    private final UUID projectId;
    private final String projectName;
    private final UUID repositoryId;
    private final String repoFullName;
    private final String branch;
    private final String commitSha;
    private final String commitMessage;
    private final String pusherName;
    private final String pusherEmail;
    private final Instant triggeredAt;
}

package com.shipit.backend.github.dto;

import com.shipit.backend.github.entity.GitHubRepository;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GitHubRepositoryResponse {

    private UUID id;
    private UUID connectionId;
    private Long githubRepoId;
    private String name;
    private String fullName;
    private String ownerLogin;
    private String ownerAvatarUrl;
    private String htmlUrl;
    private String cloneUrl;
    private String defaultBranch;
    private boolean isPrivate;
    private Long webhookId;
    private Instant createdAt;
    private Instant updatedAt;

    public static GitHubRepositoryResponse fromEntity(GitHubRepository repo) {
        if (repo == null) {
            return null;
        }
        return GitHubRepositoryResponse.builder()
                .id(repo.getId())
                .connectionId(repo.getConnection() != null ? repo.getConnection().getId() : null)
                .githubRepoId(repo.getGithubRepoId())
                .name(repo.getName())
                .fullName(repo.getFullName())
                .ownerLogin(repo.getOwnerLogin())
                .ownerAvatarUrl(repo.getOwnerAvatarUrl())
                .htmlUrl(repo.getHtmlUrl())
                .cloneUrl(repo.getCloneUrl())
                .defaultBranch(repo.getDefaultBranch())
                .isPrivate(repo.isPrivate())
                .webhookId(repo.getWebhookId())
                .createdAt(repo.getCreatedAt())
                .updatedAt(repo.getUpdatedAt())
                .build();
    }
}

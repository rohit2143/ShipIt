package com.shipit.backend.github.dto;

import com.shipit.backend.github.entity.GitHubConnection;
import com.shipit.backend.github.entity.GitHubConnectionStatus;
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
public class GitHubConnectionResponse {

    private UUID id;
    private Long installationId;
    private Long accountId;
    private String accountLogin;
    private String accountType;
    private String accountAvatarUrl;
    private String repositorySelection;
    private GitHubConnectionStatus status;
    private Instant connectedAt;

    public static GitHubConnectionResponse fromEntity(GitHubConnection connection) {
        if (connection == null) {
            return null;
        }
        return GitHubConnectionResponse.builder()
                .id(connection.getId())
                .installationId(connection.getInstallationId())
                .accountId(connection.getAccountId())
                .accountLogin(connection.getAccountLogin())
                .accountType(connection.getAccountType())
                .accountAvatarUrl(connection.getAccountAvatarUrl())
                .repositorySelection(connection.getRepositorySelection())
                .status(connection.getStatus())
                .connectedAt(connection.getConnectedAt())
                .build();
    }
}

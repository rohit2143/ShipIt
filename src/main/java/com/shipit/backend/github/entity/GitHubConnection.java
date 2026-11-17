package com.shipit.backend.github.entity;

import com.shipit.backend.common.entity.BaseEntity;
import com.shipit.backend.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "github_connections",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_github_connections_installation_id", columnNames = {"installation_id"})
        },
        indexes = {
                @Index(name = "idx_github_connections_user_id", columnList = "user_id"),
                @Index(name = "idx_github_connections_status", columnList = "status")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GitHubConnection extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "installation_id", nullable = false, unique = true)
    private Long installationId;

    @Column(name = "account_id")
    private Long accountId;

    @Column(name = "account_login", length = 100)
    private String accountLogin;

    @Column(name = "account_type", length = 20)
    private String accountType;

    @Column(name = "account_avatar_url", length = 512)
    private String accountAvatarUrl;

    @Column(name = "repository_selection", length = 20)
    private String repositorySelection;

    @Column(name = "permissions", columnDefinition = "TEXT")
    private String permissions;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private GitHubConnectionStatus status = GitHubConnectionStatus.PENDING_VERIFICATION;

    @Column(name = "connected_at")
    private Instant connectedAt;
}

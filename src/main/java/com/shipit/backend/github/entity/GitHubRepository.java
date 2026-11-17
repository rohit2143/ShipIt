package com.shipit.backend.github.entity;

import com.shipit.backend.common.entity.BaseEntity;
import com.shipit.backend.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "github_repositories",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_github_repositories_repo_id", columnNames = {"github_repo_id"})
        },
        indexes = {
                @Index(name = "idx_github_repositories_user_id", columnList = "user_id"),
                @Index(name = "idx_github_repositories_connection_id", columnList = "connection_id"),
                @Index(name = "idx_github_repositories_full_name", columnList = "full_name")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GitHubRepository extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "connection_id", nullable = false)
    private GitHubConnection connection;

    @Column(name = "github_repo_id", nullable = false, unique = true)
    private Long githubRepoId;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "full_name", nullable = false, length = 200)
    private String fullName;

    @Column(name = "owner_login", nullable = false, length = 100)
    private String ownerLogin;

    @Column(name = "owner_avatar_url", length = 512)
    private String ownerAvatarUrl;

    @Column(name = "html_url", nullable = false, length = 512)
    private String htmlUrl;

    @Column(name = "clone_url", nullable = false, length = 512)
    private String cloneUrl;

    @Column(name = "default_branch", nullable = false, length = 100)
    private String defaultBranch;

    @Column(name = "is_private", nullable = false)
    private boolean isPrivate;

    @Column(name = "webhook_id")
    private Long webhookId;
}

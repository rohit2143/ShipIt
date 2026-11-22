package com.shipit.backend.project.entity;

import com.shipit.backend.common.entity.BaseEntity;
import com.shipit.backend.github.entity.GitHubRepository;
import com.shipit.backend.user.entity.User;
import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.*;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Entity
@Table(
        name = "projects",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_projects_user_normalized_name",
                        columnNames = {"user_id", "normalized_name"}
                )
        },
        indexes = {
                @Index(name = "idx_projects_user_id", columnList = "user_id"),
                @Index(name = "idx_projects_repository_id", columnList = "repository_id"),
                @Index(
                        name = "idx_projects_webhook_lookup",
                        columnList = "repository_id, deployment_branch, auto_deploy, status"
                )
        }
)
@Check(constraints = "port >= 1 AND port <= 65535", name = "chk_projects_port_range")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Project extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "repository_id", nullable = false)
    @OnDelete(action = OnDeleteAction.RESTRICT)
    private GitHubRepository repository;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "normalized_name", nullable = false, length = 100)
    private String normalizedName;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "deployment_branch", nullable = false, length = 100)
    private String deploymentBranch;

    @Builder.Default
    @Column(name = "root_directory", nullable = false, length = 255)
    private String rootDirectory = "/";

    @Column(name = "build_command", length = 500)
    private String buildCommand;

    @Column(name = "start_command", length = 500)
    private String startCommand;

    @Min(1)
    @Max(65535)
    @Builder.Default
    @Column(name = "port", nullable = false)
    private Integer port = 8080;

    @Builder.Default
    @Column(name = "auto_deploy", nullable = false)
    private boolean autoDeploy = true;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private ProjectStatus status = ProjectStatus.ACTIVE;

    @Builder.Default
    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private List<ProjectEnvironmentVariable> environmentVariables = new ArrayList<>();

    @PrePersist
    @PreUpdate
    private void syncNormalizedName() {
        if (this.name != null) {
            this.normalizedName = this.name.trim().toLowerCase(Locale.ROOT);
        }
    }
}

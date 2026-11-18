package com.shipit.backend.deployment.entity;

import com.shipit.backend.common.entity.BaseEntity;
import com.shipit.backend.project.entity.Project;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "deployments",
        indexes = {
                @Index(name = "idx_deployments_project_id", columnList = "project_id"),
                @Index(name = "idx_deployments_project_created_at", columnList = "project_id, created_at"),
                @Index(name = "idx_deployments_status", columnList = "status")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Deployment extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Project project;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private DeploymentStatus status = DeploymentStatus.QUEUED;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false, length = 30)
    private DeploymentTrigger trigger;

    @Column(name = "commit_sha", length = 40)
    private String commitSha;

    @Column(name = "commit_message", length = 1000)
    private String commitMessage;

    @Column(name = "commit_branch", nullable = false, length = 100)
    private String commitBranch;

    @Column(name = "commit_author", length = 100)
    private String commitAuthor;

    @Column(name = "queued_at", nullable = false)
    private Instant queuedAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "docker_image_tag", length = 255)
    private String dockerImageTag;

    @Column(name = "container_id", length = 100)
    private String containerId;

    @Column(name = "container_port")
    private Integer containerPort;

    @PrePersist
    public void prePersist() {
        if (this.queuedAt == null) {
            this.queuedAt = Instant.now();
        }
        if (this.status == null) {
            this.status = DeploymentStatus.QUEUED;
        }
    }

    public void transitionTo(DeploymentStatus newStatus) {
        if (!this.status.canTransitionTo(newStatus)) {
            throw new IllegalStateException(
                    String.format("Invalid deployment status transition from %s to %s", this.status, newStatus)
            );
        }
        this.status = newStatus;
        if (newStatus == DeploymentStatus.CLONING && this.startedAt == null) {
            this.startedAt = Instant.now();
        }
        if (newStatus.isTerminal()) {
            this.finishedAt = Instant.now();
        }
    }

    public void markFailed(String errorMessage) {
        transitionTo(DeploymentStatus.FAILED);
        this.errorMessage = errorMessage != null && errorMessage.length() > 1000
                ? errorMessage.substring(0, 1000)
                : errorMessage;
    }

    public void cancel() {
        transitionTo(DeploymentStatus.CANCELLED);
    }
}

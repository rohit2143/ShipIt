package com.shipit.backend.job.entity;

import com.shipit.backend.common.entity.BaseEntity;
import com.shipit.backend.deployment.entity.Deployment;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "deployment_jobs",
        indexes = {
                @Index(name = "idx_jobs_status_priority_queued", columnList = "status, priority, queued_at"),
                @Index(name = "idx_jobs_deployment_id", columnList = "deployment_id"),
                @Index(name = "idx_jobs_status_heartbeat", columnList = "status, heartbeat_at"),
                @Index(name = "idx_jobs_claimed_by_status", columnList = "claimed_by, status")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeploymentJob extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "deployment_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Deployment deployment;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 30)
    @Builder.Default
    private JobType type = JobType.DEPLOYMENT;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private JobStatus status = JobStatus.PENDING;

    @Column(name = "priority", nullable = false)
    @Builder.Default
    private int priority = 0;

    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private int retryCount = 0;

    @Column(name = "max_retries", nullable = false)
    @Builder.Default
    private int maxRetries = 3;

    @Column(name = "claimed_by", length = 255)
    private String claimedBy;

    @Column(name = "queued_at", nullable = false)
    private Instant queuedAt;

    @Column(name = "claimed_at")
    private Instant claimedAt;

    @Column(name = "heartbeat_at")
    private Instant heartbeatAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "error_message", length = 2000)
    private String errorMessage;

    @PrePersist
    public void prePersist() {
        if (this.queuedAt == null) {
            this.queuedAt = Instant.now();
        }
    }

    public void transitionTo(JobStatus newStatus) {
        if (!this.status.canTransitionTo(newStatus)) {
            throw new IllegalStateException(
                    "Invalid job transition from " + this.status + " to " + newStatus
            );
        }
        this.status = newStatus;
    }

    public void claim(String workerId) {
        transitionTo(JobStatus.RUNNING);
        this.claimedBy = workerId;
        this.claimedAt = Instant.now();
        this.heartbeatAt = this.claimedAt;
    }

    public void updateHeartbeat() {
        if (this.status != JobStatus.RUNNING) {
            throw new IllegalStateException("Cannot update heartbeat for job in status " + this.status);
        }
        this.heartbeatAt = Instant.now();
    }

    public void complete() {
        transitionTo(JobStatus.COMPLETED);
        this.finishedAt = Instant.now();
    }

    public void fail(String errorMessage) {
        transitionTo(JobStatus.FAILED);
        this.errorMessage = sanitizeErrorMessage(errorMessage);
        this.finishedAt = Instant.now();
    }

    public void cancel() {
        transitionTo(JobStatus.CANCELLED);
        this.finishedAt = Instant.now();
    }

    public void scheduleRetry(String reason) {
        transitionTo(JobStatus.PENDING);
        this.retryCount++;
        this.claimedBy = null;
        this.claimedAt = null;
        this.heartbeatAt = null;
        this.errorMessage = sanitizeErrorMessage(reason);
    }

    public boolean canRetry() {
        return this.retryCount < this.maxRetries;
    }

    private String sanitizeErrorMessage(String message) {
        if (message == null) {
            return null;
        }
        return message.length() > 2000 ? message.substring(0, 2000) : message;
    }
}

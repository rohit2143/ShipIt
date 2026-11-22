package com.shipit.backend.log.entity;

import com.shipit.backend.build.logging.LogSource;
import com.shipit.backend.common.entity.BaseEntity;
import com.shipit.backend.deployment.entity.Deployment;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.Instant;
import java.util.UUID;

/**
 * Persisted structured log record associated with a deployment execution.
 */
@Entity
@Table(
        name = "deployment_logs",
        indexes = {
                @Index(name = "idx_deployment_logs_dep_seq", columnList = "deployment_id, sequence_number"),
                @Index(name = "idx_deployment_logs_dep_stage", columnList = "deployment_id, stage"),
                @Index(name = "idx_deployment_logs_timestamp", columnList = "timestamp")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeploymentLog extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "deployment_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Deployment deployment;

    @Column(name = "sequence_number", nullable = false)
    private long sequenceNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "stage", nullable = false, length = 30)
    private DeploymentStage stage;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 20)
    @Builder.Default
    private LogSource source = LogSource.SYSTEM;

    @Column(name = "message", columnDefinition = "TEXT", nullable = false)
    private String message;

    @Column(name = "timestamp", nullable = false)
    @Builder.Default
    private Instant timestamp = Instant.now();

    @PrePersist
    public void prePersist() {
        if (this.timestamp == null) {
            this.timestamp = Instant.now();
        }
    }

    /**
     * Formats the log entry into a standardized single-line console output.
     */
    public String toFormattedString() {
        return String.format("[%s] [%s] [%s] %s",
                timestamp,
                stage != null ? stage : DeploymentStage.SYSTEM,
                source != null ? source : LogSource.SYSTEM,
                message != null ? message : "");
    }
}

package com.shipit.backend.project.entity;

import com.shipit.backend.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.util.UUID;

@Entity
@Table(
        name = "project_environment_variables",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_project_env_vars_project_key",
                        columnNames = {"project_id", "key"}
                )
        },
        indexes = {
                @Index(name = "idx_project_env_vars_project_id", columnList = "project_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProjectEnvironmentVariable extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "project_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_project_env_vars_project_id")
    )
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Project project;

    @Column(name = "key", nullable = false, length = 255)
    private String key;

    /**
     * Plaintext when is_secret = false.
     * AES-256-GCM ciphertext envelope (v1:base64) when is_secret = true.
     */
    @Column(name = "value", nullable = false, columnDefinition = "TEXT")
    private String value;

    @Builder.Default
    @Column(name = "is_secret", nullable = false)
    private boolean isSecret = false;
}

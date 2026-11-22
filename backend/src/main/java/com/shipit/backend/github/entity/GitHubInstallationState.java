package com.shipit.backend.github.entity;

import com.shipit.backend.common.entity.BaseEntity;
import com.shipit.backend.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "github_installation_states",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_github_installation_states_hash", columnNames = {"state_hash"})
        },
        indexes = {
                @Index(name = "idx_github_installation_states_user", columnList = "user_id")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GitHubInstallationState extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "state_hash", nullable = false, unique = true, length = 64)
    private String stateHash;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Builder.Default
    @Column(name = "used", nullable = false)
    private boolean used = false;
}

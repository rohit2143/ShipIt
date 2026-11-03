package com.shipit.backend.auth.entity;

import com.shipit.backend.common.entity.BaseEntity;
import com.shipit.backend.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "user_auth_identities",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_identity_provider_user_id", columnNames = {"provider", "provider_user_id"}),
                @UniqueConstraint(name = "uk_identity_user_provider", columnNames = {"user_id", "provider"})
        },
        indexes = {
                @Index(name = "idx_identities_user_id", columnList = "user_id"),
                @Index(name = "idx_identities_provider_lookup", columnList = "provider, provider_user_id")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserAuthIdentity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 20)
    private AuthProvider provider;

    @Column(name = "provider_user_id", nullable = false, length = 255)
    private String providerUserId;

    @Column(name = "password_hash", length = 255)
    private String passwordHash;
}

package com.shipit.backend.user.dto;

import com.shipit.backend.auth.entity.AuthProvider;
import com.shipit.backend.auth.entity.UserAuthIdentity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IdentityResponse {

    private UUID id;
    private AuthProvider provider;
    private String providerUserId;
    private Instant createdAt;

    public static IdentityResponse fromEntity(UserAuthIdentity identity) {
        return IdentityResponse.builder()
                .id(identity.getId())
                .provider(identity.getProvider())
                .providerUserId(identity.getProviderUserId())
                .createdAt(identity.getCreatedAt())
                .build();
    }
}

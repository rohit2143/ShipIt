package com.shipit.backend.project.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.shipit.backend.project.entity.ProjectEnvironmentVariable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EnvironmentVariableResponse {

    public static final String MASKED_VALUE = "********";

    private UUID id;
    private String key;
    private String value;

    @JsonProperty("isSecret")
    private boolean isSecret;

    private Instant createdAt;
    private Instant updatedAt;

    public static EnvironmentVariableResponse fromEntity(ProjectEnvironmentVariable entity) {
        if (entity == null) {
            return null;
        }
        return EnvironmentVariableResponse.builder()
                .id(entity.getId())
                .key(entity.getKey())
                .value(entity.isSecret() ? MASKED_VALUE : entity.getValue())
                .isSecret(entity.isSecret())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}

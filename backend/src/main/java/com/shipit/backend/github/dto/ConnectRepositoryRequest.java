package com.shipit.backend.github.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConnectRepositoryRequest {

    @NotNull(message = "connectionId is required")
    private UUID connectionId;

    @NotNull(message = "githubRepoId is required")
    private Long githubRepoId;
}

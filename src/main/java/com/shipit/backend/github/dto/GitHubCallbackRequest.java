package com.shipit.backend.github.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GitHubCallbackRequest {

    @NotNull(message = "installationId is required")
    private Long installationId;

    private String setupAction;

    @NotBlank(message = "state is required")
    private String state;
}

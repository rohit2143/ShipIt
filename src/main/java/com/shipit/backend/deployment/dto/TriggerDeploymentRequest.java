package com.shipit.backend.deployment.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TriggerDeploymentRequest {

    @Size(max = 100, message = "Branch name must not exceed 100 characters")
    private String branch;

    @Size(max = 40, message = "Commit SHA must not exceed 40 characters")
    private String commitSha;

    @Size(max = 1000, message = "Commit message must not exceed 1000 characters")
    private String commitMessage;
}

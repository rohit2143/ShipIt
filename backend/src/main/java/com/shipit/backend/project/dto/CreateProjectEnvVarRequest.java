package com.shipit.backend.project.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateProjectEnvVarRequest {

    @NotBlank(message = "Environment variable key is required")
    @Pattern(
            regexp = "^[A-Z_][A-Z0-9_]*$",
            message = "Key must start with a letter or underscore and contain only uppercase letters, digits, and underscores"
    )
    private String key;

    @NotNull(message = "Environment variable value is required")
    private String value;

    @Builder.Default
    private Boolean isSecret = false;
}

package com.shipit.backend.project.dto;

import com.shipit.backend.project.entity.ProjectStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateProjectRequest {

    @Size(min = 2, max = 100, message = "Project name must be between 2 and 100 characters")
    @Pattern(
            regexp = "^[a-zA-Z0-9_-]+( [a-zA-Z0-9_-]+)*$",
            message = "Project name may contain alphanumeric characters, hyphens, underscores, and single spaces between words"
    )
    private String name;

    @Size(max = 500, message = "Description must not exceed 500 characters")
    private String description;

    @Size(max = 100, message = "Deployment branch must not exceed 100 characters")
    private String deploymentBranch;

    @Size(max = 255, message = "Root directory must not exceed 255 characters")
    private String rootDirectory;

    @Size(max = 500, message = "Build command must not exceed 500 characters")
    private String buildCommand;

    @Size(max = 500, message = "Start command must not exceed 500 characters")
    private String startCommand;

    @Min(value = 1, message = "Port must be at least 1")
    @Max(value = 65535, message = "Port cannot exceed 65535")
    private Integer port;

    private Boolean autoDeploy;

    private ProjectStatus status;
}

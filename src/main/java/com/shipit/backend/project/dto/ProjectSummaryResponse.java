package com.shipit.backend.project.dto;

import com.shipit.backend.project.entity.Project;
import com.shipit.backend.project.entity.ProjectStatus;
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
public class ProjectSummaryResponse {

    private UUID id;
    private String name;
    private String description;
    private UUID repositoryId;
    private String repositoryFullName;
    private String deploymentBranch;
    private Integer port;
    private boolean autoDeploy;
    private ProjectStatus status;
    private Instant createdAt;
    private Instant updatedAt;

    public static ProjectSummaryResponse fromEntity(Project project) {
        if (project == null) {
            return null;
        }
        return ProjectSummaryResponse.builder()
                .id(project.getId())
                .name(project.getName())
                .description(project.getDescription())
                .repositoryId(project.getRepository() != null ? project.getRepository().getId() : null)
                .repositoryFullName(project.getRepository() != null ? project.getRepository().getFullName() : null)
                .deploymentBranch(project.getDeploymentBranch())
                .port(project.getPort())
                .autoDeploy(project.isAutoDeploy())
                .status(project.getStatus())
                .createdAt(project.getCreatedAt())
                .updatedAt(project.getUpdatedAt())
                .build();
    }
}

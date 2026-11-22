package com.shipit.backend.project.dto;

import com.shipit.backend.github.entity.GitHubRepository;
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
public class ProjectResponse {

    private UUID id;
    private String name;
    private String description;
    private UUID repositoryId;
    private String repositoryName;
    private String repositoryFullName;
    private String repositoryHtmlUrl;
    private String repositoryCloneUrl;
    private String repositoryDefaultBranch;
    private boolean repositoryIsPrivate;
    private String deploymentBranch;
    private String rootDirectory;
    private String buildCommand;
    private String startCommand;
    private Integer port;
    private boolean autoDeploy;
    private ProjectStatus status;
    private int environmentVariablesCount;
    private Instant createdAt;
    private Instant updatedAt;

    public static ProjectResponse fromEntity(Project project) {
        int count = (project != null && project.getEnvironmentVariables() != null)
                ? project.getEnvironmentVariables().size()
                : 0;
        return fromEntity(project, count);
    }

    public static ProjectResponse fromEntity(Project project, int environmentVariablesCount) {
        if (project == null) {
            return null;
        }
        GitHubRepository repo = project.getRepository();
        return ProjectResponse.builder()
                .id(project.getId())
                .name(project.getName())
                .description(project.getDescription())
                .repositoryId(repo != null ? repo.getId() : null)
                .repositoryName(repo != null ? repo.getName() : null)
                .repositoryFullName(repo != null ? repo.getFullName() : null)
                .repositoryHtmlUrl(repo != null ? repo.getHtmlUrl() : null)
                .repositoryCloneUrl(repo != null ? repo.getCloneUrl() : null)
                .repositoryDefaultBranch(repo != null ? repo.getDefaultBranch() : null)
                .repositoryIsPrivate(repo != null && repo.isPrivate())
                .deploymentBranch(project.getDeploymentBranch())
                .rootDirectory(project.getRootDirectory())
                .buildCommand(project.getBuildCommand())
                .startCommand(project.getStartCommand())
                .port(project.getPort())
                .autoDeploy(project.isAutoDeploy())
                .status(project.getStatus())
                .environmentVariablesCount(environmentVariablesCount)
                .createdAt(project.getCreatedAt())
                .updatedAt(project.getUpdatedAt())
                .build();
    }
}

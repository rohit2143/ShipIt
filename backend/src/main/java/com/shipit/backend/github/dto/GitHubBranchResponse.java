package com.shipit.backend.github.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GitHubBranchResponse {

    private String name;

    private String sha;

    private boolean isProtected;

    public static GitHubBranchResponse fromDto(GitHubBranchDto dto) {
        if (dto == null) {
            return null;
        }
        return GitHubBranchResponse.builder()
                .name(dto.getName())
                .sha(dto.getCommit() != null ? dto.getCommit().getSha() : null)
                .isProtected(dto.isProtected())
                .build();
    }
}

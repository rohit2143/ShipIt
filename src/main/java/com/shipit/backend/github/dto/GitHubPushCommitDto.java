package com.shipit.backend.github.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class GitHubPushCommitDto {

    private String id;
    private String message;
    private String timestamp;
    private String url;
    private GitHubPushUserDto author;
    private GitHubPushUserDto committer;
    private List<String> added;
    private List<String> removed;
    private List<String> modified;
}

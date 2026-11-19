package com.shipit.backend.build.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Data transfer object representing the captured build output logs for a deployment.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BuildLogsResponse {

    private UUID deploymentId;
    private String logs;
    private int lineCount;
}

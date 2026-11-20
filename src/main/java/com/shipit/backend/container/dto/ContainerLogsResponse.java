package com.shipit.backend.container.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContainerLogsResponse {
    private UUID deploymentId;
    private String containerId;
    private int lines;
    private String logs;
}

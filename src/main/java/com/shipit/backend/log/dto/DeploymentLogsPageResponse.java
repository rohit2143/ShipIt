package com.shipit.backend.log.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/**
 * Paginated response wrapper for deployment execution logs.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeploymentLogsPageResponse {

    private UUID deploymentId;
    private List<DeploymentLogDto> logs;
    private long totalLines;
    private long maxSequenceNumber;
    private int page;
    private int size;
    private boolean hasMore;
}

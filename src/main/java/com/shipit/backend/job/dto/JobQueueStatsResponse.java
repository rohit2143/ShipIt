package com.shipit.backend.job.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobQueueStatsResponse {

    private long pendingCount;
    private long runningCount;
    private long completedCount;
    private long failedCount;
    private long cancelledCount;
    private long totalCount;
}

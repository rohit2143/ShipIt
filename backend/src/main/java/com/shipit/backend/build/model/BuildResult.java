package com.shipit.backend.build.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Outcome and performance metrics of a completed Docker image build.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BuildResult {

    private String imageTag;
    private String imageId;
    private Instant startedAt;
    private Instant finishedAt;
    private long durationSeconds;
    private boolean success;
}

package com.shipit.backend.log.dto;

import com.shipit.backend.build.logging.LogSource;
import com.shipit.backend.log.entity.DeploymentLog;
import com.shipit.backend.log.entity.DeploymentStage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Data transfer object representing an individual deployment log line.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeploymentLogDto {

    private long sequenceNumber;
    private DeploymentStage stage;
    private LogSource source;
    private String message;
    private Instant timestamp;

    /**
     * Converts a JPA DeploymentLog entity into its DTO representation.
     */
    public static DeploymentLogDto fromEntity(DeploymentLog entity) {
        if (entity == null) {
            return null;
        }
        return DeploymentLogDto.builder()
                .sequenceNumber(entity.getSequenceNumber())
                .stage(entity.getStage())
                .source(entity.getSource())
                .message(entity.getMessage())
                .timestamp(entity.getTimestamp())
                .build();
    }

    /**
     * Formats the log line for plain-text streaming and download.
     */
    public String toFormattedString() {
        return String.format("[%s] [%s] [%s] %s",
                timestamp != null ? timestamp : Instant.now(),
                stage != null ? stage : DeploymentStage.SYSTEM,
                source != null ? source : LogSource.SYSTEM,
                message != null ? message : "");
    }
}

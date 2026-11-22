package com.shipit.backend.build.logging;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * An immutable timestamped log record captured during build execution.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BuildLogEntry {

    @Builder.Default
    private Instant timestamp = Instant.now();

    private LogSource source;
    private String message;

    public String toFormattedString() {
        return String.format("[%s] [%s] %s",
                timestamp,
                source != null ? source : LogSource.SYSTEM,
                message != null ? message : "");
    }
}

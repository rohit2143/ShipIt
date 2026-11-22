package com.shipit.backend.build.process;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Result metrics and termination status of an executed external process.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessResult {

    private int exitCode;
    private boolean timedOut;

    public boolean isSuccess() {
        return exitCode == 0 && !timedOut;
    }
}

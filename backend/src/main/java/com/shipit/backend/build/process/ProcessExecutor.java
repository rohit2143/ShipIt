package com.shipit.backend.build.process;

import com.shipit.backend.build.logging.BuildLogBuffer;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/**
 * Execution interface for running external system commands with timeout and streaming log capture.
 */
public interface ProcessExecutor {

    /**
     * Executes the specified command with output streaming into the provided log buffer.
     *
     * @param command Command line tokens.
     * @param workingDir Working directory for the process (or null for default).
     * @param logBuffer Sink buffer for stdout/stderr lines.
     * @param timeoutSeconds Maximum duration to wait before destroying the process.
     * @param sensitiveValues Strings to mask from command logs, outputs, and errors.
     * @return Execution result including exit code and timeout indicator.
     */
    ProcessResult execute(
            List<String> command,
            Path workingDir,
            BuildLogBuffer logBuffer,
            long timeoutSeconds,
            Set<String> sensitiveValues
    );
}

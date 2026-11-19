package com.shipit.backend.build.process;

import com.shipit.backend.build.logging.BuildLogBuffer;
import com.shipit.backend.build.logging.LogSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;

/**
 * Standard ProcessBuilder-based executor that captures process streams asynchronously,
 * enforces execution timeouts, masks sensitive secrets, and terminates stalled processes.
 */
@Slf4j
@Component
public class DefaultProcessExecutor implements ProcessExecutor {

    private static final String MASK_REPLACEMENT = "***MASKED***";

    @Override
    public ProcessResult execute(
            List<String> command,
            Path workingDir,
            BuildLogBuffer logBuffer,
            long timeoutSeconds,
            Set<String> sensitiveValues
    ) {
        if (command == null || command.isEmpty()) {
            throw new IllegalArgumentException("Command must not be empty");
        }

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        if (workingDir != null) {
            processBuilder.directory(workingDir.toFile());
        }

        String displayCmd = maskSensitive(String.join(" ", command), sensitiveValues);
        if (logBuffer != null) {
            logBuffer.append(LogSource.SYSTEM, "$ " + displayCmd);
        }
        log.debug("Executing command: {}", displayCmd);

        Process process;
        try {
            process = processBuilder.start();
        } catch (Exception ex) {
            String errorMsg = "Failed to start process [" + displayCmd + "]: " + ex.getMessage();
            if (logBuffer != null) {
                logBuffer.append(LogSource.STDERR, errorMsg);
            }
            log.error("{}", errorMsg);
            return ProcessResult.builder().exitCode(-1).timedOut(false).build();
        }

        ExecutorService streamReaderPool = Executors.newFixedThreadPool(2);
        Future<?> stdoutFuture = streamReaderPool.submit(() ->
                readStream(process.getInputStream(), LogSource.STDOUT, logBuffer, sensitiveValues));
        Future<?> stderrFuture = streamReaderPool.submit(() ->
                readStream(process.getErrorStream(), LogSource.STDERR, logBuffer, sensitiveValues));

        boolean completed;
        boolean timedOut = false;
        try {
            completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!completed) {
                timedOut = true;
                log.warn("Process timed out after {}s: {}", timeoutSeconds, displayCmd);
                if (logBuffer != null) {
                    logBuffer.append(LogSource.STDERR, String.format("Process timed out after %ds", timeoutSeconds));
                }
                process.destroy();
                if (!process.waitFor(5, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                }
            }
            stdoutFuture.get(5, TimeUnit.SECONDS);
            stderrFuture.get(5, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("Process execution interrupted: {}", displayCmd);
            if (logBuffer != null) {
                logBuffer.append(LogSource.STDERR, "Process execution was interrupted");
            }
            process.destroyForcibly();
            return ProcessResult.builder().exitCode(-1).timedOut(false).build();
        } catch (Exception ex) {
            log.warn("Error waiting for stream readers: {}", ex.getMessage());
        } finally {
            streamReaderPool.shutdownNow();
        }

        int exitCode = timedOut ? -1 : process.exitValue();
        return ProcessResult.builder()
                .exitCode(exitCode)
                .timedOut(timedOut)
                .build();
    }

    private void readStream(
            InputStream is,
            LogSource source,
            BuildLogBuffer logBuffer,
            Set<String> sensitiveValues
    ) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String masked = maskSensitive(line, sensitiveValues);
                if (logBuffer != null) {
                    logBuffer.append(source, masked);
                }
            }
        } catch (Exception ex) {
            log.debug("Stream reading terminated: {}", ex.getMessage());
        }
    }

    private String maskSensitive(String input, Set<String> sensitiveValues) {
        if (input == null || sensitiveValues == null || sensitiveValues.isEmpty()) {
            return input;
        }
        String result = input;
        for (String sensitive : sensitiveValues) {
            if (sensitive != null && !sensitive.isBlank()) {
                result = result.replace(sensitive, MASK_REPLACEMENT);
            }
        }
        return result;
    }
}

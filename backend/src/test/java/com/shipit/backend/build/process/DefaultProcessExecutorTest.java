package com.shipit.backend.build.process;

import com.shipit.backend.build.logging.BuildLogBuffer;
import com.shipit.backend.build.logging.BuildLogEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultProcessExecutorTest {

    private DefaultProcessExecutor executor;
    private BuildLogBuffer logBuffer;

    @BeforeEach
    void setUp() {
        executor = new DefaultProcessExecutor();
        logBuffer = new BuildLogBuffer(100, 200);
    }

    @Test
    @DisplayName("DefaultProcessExecutor executes command and captures stdout into log buffer")
    void testExecuteCommand_Success() {
        List<String> command = List.of("sh", "-c", "echo 'Hello ShipIt Builder'");

        ProcessResult result = executor.execute(command, null, logBuffer, 10, Set.of());

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getExitCode()).isEqualTo(0);
        assertThat(result.isTimedOut()).isFalse();

        List<BuildLogEntry> entries = logBuffer.getEntries();
        assertThat(entries).isNotEmpty();
        assertThat(logBuffer.getAllLogsAsString()).contains("Hello ShipIt Builder");
    }

    @Test
    @DisplayName("DefaultProcessExecutor masks sensitive tokens in command and output")
    void testSensitiveMasking() {
        String secretToken = "ghs_SecretToken123456789";
        List<String> command = List.of("sh", "-c", "echo 'Cloning with token: " + secretToken + "'");

        ProcessResult result = executor.execute(command, null, logBuffer, 10, Set.of(secretToken));

        assertThat(result.isSuccess()).isTrue();
        String logs = logBuffer.getAllLogsAsString();
        assertThat(logs).doesNotContain(secretToken);
        assertThat(logs).contains("***MASKED***");
    }

    @Test
    @DisplayName("DefaultProcessExecutor handles non-zero exit codes")
    void testNonZeroExitCode() {
        List<String> command = List.of("sh", "-c", "exit 7");

        ProcessResult result = executor.execute(command, null, logBuffer, 10, Set.of());

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getExitCode()).isEqualTo(7);
        assertThat(result.isTimedOut()).isFalse();
    }

    @Test
    @DisplayName("DefaultProcessExecutor terminates process and marks timedOut when execution exceeds timeout")
    void testProcessTimeout() {
        List<String> command = List.of("sh", "-c", "sleep 10");

        ProcessResult result = executor.execute(command, null, logBuffer, 1, Set.of());

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.isTimedOut()).isTrue();
        assertThat(logBuffer.getAllLogsAsString()).contains("timed out after 1s");
    }

    @Test
    @DisplayName("DefaultProcessExecutor rejects empty or null command")
    void testEmptyCommand() {
        assertThatThrownBy(() -> executor.execute(null, null, logBuffer, 10, Set.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> executor.execute(List.of(), null, logBuffer, 10, Set.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

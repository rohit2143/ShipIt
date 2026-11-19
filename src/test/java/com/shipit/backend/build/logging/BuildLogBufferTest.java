package com.shipit.backend.build.logging;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BuildLogBufferTest {

    @Test
    @DisplayName("BuildLogBuffer appends lines with correct source attribution and timestamps")
    void testBasicAppend() {
        BuildLogBuffer buffer = new BuildLogBuffer(100, 200);

        buffer.append("System initialization");
        buffer.append(LogSource.STDOUT, "Building project with Maven");
        buffer.append(LogSource.STDERR, "Warning: deprecated API used");

        assertThat(buffer.size()).isEqualTo(3);
        assertThat(buffer.isEmpty()).isFalse();

        List<BuildLogEntry> entries = buffer.getEntries();
        assertThat(entries.get(0).getSource()).isEqualTo(LogSource.SYSTEM);
        assertThat(entries.get(0).getMessage()).isEqualTo("System initialization");
        assertThat(entries.get(0).getTimestamp()).isNotNull();

        assertThat(entries.get(1).getSource()).isEqualTo(LogSource.STDOUT);
        assertThat(entries.get(1).getMessage()).isEqualTo("Building project with Maven");

        assertThat(entries.get(2).getSource()).isEqualTo(LogSource.STDERR);
        assertThat(entries.get(2).getMessage()).isEqualTo("Warning: deprecated API used");
    }

    @Test
    @DisplayName("BuildLogBuffer splits multiline strings into individual entries")
    void testMultilineSplitting() {
        BuildLogBuffer buffer = new BuildLogBuffer(100, 200);

        String multiline = "Line 1\nLine 2\r\nLine 3";
        buffer.append(LogSource.STDOUT, multiline);

        assertThat(buffer.size()).isEqualTo(3);
        List<BuildLogEntry> entries = buffer.getEntries();
        assertThat(entries.get(0).getMessage()).isEqualTo("Line 1");
        assertThat(entries.get(1).getMessage()).isEqualTo("Line 2");
        assertThat(entries.get(2).getMessage()).isEqualTo("Line 3");
    }

    @Test
    @DisplayName("BuildLogBuffer truncates individual lines exceeding maxLineLength")
    void testLineLengthTruncation() {
        int maxLineLength = 50;
        BuildLogBuffer buffer = new BuildLogBuffer(10, maxLineLength);

        String veryLongLine = "A".repeat(100);
        buffer.append(LogSource.STDOUT, veryLongLine);

        assertThat(buffer.size()).isEqualTo(1);
        String storedMessage = buffer.getEntries().get(0).getMessage();
        assertThat(storedMessage).hasSize(maxLineLength);
        assertThat(storedMessage).endsWith("... [truncated]");
    }

    @Test
    @DisplayName("BuildLogBuffer circular behavior evicts oldest lines when capacity is exceeded")
    void testCircularEviction() {
        int maxLines = 5;
        BuildLogBuffer buffer = new BuildLogBuffer(maxLines, 200);

        for (int i = 1; i <= 8; i++) {
            buffer.append(LogSource.STDOUT, "Log line #" + i);
        }

        assertThat(buffer.size()).isEqualTo(5);
        assertThat(buffer.getDroppedLinesCount()).isEqualTo(3);

        List<BuildLogEntry> entries = buffer.getEntries();
        assertThat(entries.get(0).getMessage()).isEqualTo("Log line #4");
        assertThat(entries.get(4).getMessage()).isEqualTo("Log line #8");

        String allLogs = buffer.getAllLogsAsString();
        assertThat(allLogs).contains("... 3 earlier log lines evicted to conserve memory ...");
        assertThat(allLogs).contains("Log line #8");
        assertThat(allLogs).doesNotContain("Log line #1");
    }

    @Test
    @DisplayName("BuildLogBuffer handles null inputs gracefully")
    void testNullInputs() {
        BuildLogBuffer buffer = new BuildLogBuffer(10, 100);

        buffer.append(null);
        buffer.append(null, null);

        assertThat(buffer.size()).isEqualTo(2);
        assertThat(buffer.getEntries().get(0).getMessage()).isEqualTo("");
        assertThat(buffer.getEntries().get(1).getMessage()).isEqualTo("");
    }

    @Test
    @DisplayName("BuildLogBuffer clear resets entries and dropped counter")
    void testClear() {
        BuildLogBuffer buffer = new BuildLogBuffer(2, 100);
        buffer.append("Line 1");
        buffer.append("Line 2");
        buffer.append("Line 3"); // evicts line 1

        assertThat(buffer.size()).isEqualTo(2);
        assertThat(buffer.getDroppedLinesCount()).isEqualTo(1);

        buffer.clear();

        assertThat(buffer.size()).isEqualTo(0);
        assertThat(buffer.isEmpty()).isTrue();
        assertThat(buffer.getDroppedLinesCount()).isEqualTo(0);
        assertThat(buffer.getAllLogsAsString()).isEqualTo("");
    }

    @Test
    @DisplayName("BuildLogBuffer appendAll appends list of lines safely")
    void testAppendAll() {
        BuildLogBuffer buffer = new BuildLogBuffer(10, 100);

        buffer.appendAll(LogSource.STDOUT, List.of("Line A", "Line B"));
        buffer.appendAll(LogSource.STDOUT, null);
        buffer.appendAll(LogSource.STDOUT, List.of());

        assertThat(buffer.size()).isEqualTo(2);
    }

    @Test
    @DisplayName("BuildLogBuffer constructor validates positive parameters")
    void testConstructorValidation() {
        assertThatThrownBy(() -> new BuildLogBuffer(0, 100))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxLogLines must be positive");

        assertThatThrownBy(() -> new BuildLogBuffer(10, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxLineLength must be positive");
    }

    @Test
    @DisplayName("BuildLogBuffer maintains thread safety under high concurrency")
    void testThreadSafety() throws InterruptedException {
        int threadCount = 8;
        int linesPerThread = 250;
        int maxCapacity = 5000;
        BuildLogBuffer buffer = new BuildLogBuffer(maxCapacity, 256);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            futures.add(CompletableFuture.runAsync(() -> {
                for (int i = 0; i < linesPerThread; i++) {
                    buffer.append(LogSource.STDOUT, "Thread-" + threadId + " output line " + i);
                }
            }, executor));
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        executor.shutdown();
        assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();

        assertThat(buffer.size()).isEqualTo(threadCount * linesPerThread);
        assertThat(buffer.getDroppedLinesCount()).isEqualTo(0);
        assertThat(buffer.getFormattedLines()).hasSize(threadCount * linesPerThread);
    }

    @Test
    @DisplayName("BuildLogEntry toFormattedString formats with timestamp and source")
    void testBuildLogEntryFormatting() {
        BuildLogEntry entry = BuildLogEntry.builder()
                .source(LogSource.STDOUT)
                .message("Test build line")
                .build();

        String formatted = entry.toFormattedString();
        assertThat(formatted).contains("[STDOUT]");
        assertThat(formatted).contains("Test build line");
    }
}

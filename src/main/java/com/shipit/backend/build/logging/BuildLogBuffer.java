package com.shipit.backend.build.logging;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Thread-safe, memory-bounded circular log buffer for build process stdout/stderr streams.
 * <p>
 * Enforces two critical constraints to operate reliably within an 8 GB RAM development environment:
 * 1. Line Count Cap: When total stored entries exceed {@code maxLogLines}, the oldest entries are
 *    evicted in FIFO order, with an eviction counter tracking dropped lines.
 * 2. Line Length Cap: Any individual log line exceeding {@code maxLineLength} characters is
 *    sanitized and truncated to prevent single minified output lines from exhausting JVM memory.
 */
public class BuildLogBuffer {

    public static final int DEFAULT_MAX_LOG_LINES = 5000;
    public static final int DEFAULT_MAX_LINE_LENGTH = 1024;
    private static final String TRUNCATION_SUFFIX = "... [truncated]";

    private final int maxLogLines;
    private final int maxLineLength;
    private final ArrayDeque<BuildLogEntry> entries;
    private final ReentrantLock lock = new ReentrantLock();

    private int droppedLinesCount = 0;

    public BuildLogBuffer() {
        this(DEFAULT_MAX_LOG_LINES, DEFAULT_MAX_LINE_LENGTH);
    }

    public BuildLogBuffer(int maxLogLines, int maxLineLength) {
        if (maxLogLines <= 0) {
            throw new IllegalArgumentException("maxLogLines must be positive");
        }
        if (maxLineLength <= 0) {
            throw new IllegalArgumentException("maxLineLength must be positive");
        }
        this.maxLogLines = maxLogLines;
        this.maxLineLength = maxLineLength;
        this.entries = new ArrayDeque<>(Math.min(maxLogLines, 512));
    }

    /**
     * Appends a log line from the default SYSTEM source.
     */
    public void append(String line) {
        append(LogSource.SYSTEM, line);
    }

    /**
     * Appends a log line attributed to the specified stream source.
     * If the string contains newline characters, it is safely split into discrete lines.
     */
    public void append(LogSource source, String line) {
        if (line == null) {
            appendSingle(source != null ? source : LogSource.SYSTEM, "");
            return;
        }

        if (line.contains("\n")) {
            String[] split = line.split("\\r?\\n", -1);
            lock.lock();
            try {
                for (String sub : split) {
                    appendSingleInternal(source != null ? source : LogSource.SYSTEM, sub);
                }
            } finally {
                lock.unlock();
            }
        } else {
            appendSingle(source != null ? source : LogSource.SYSTEM, line);
        }
    }

    /**
     * Appends a batch of log lines.
     */
    public void appendAll(LogSource source, List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return;
        }
        lock.lock();
        try {
            for (String line : lines) {
                append(source, line);
            }
        } finally {
            lock.unlock();
        }
    }

    private void appendSingle(LogSource source, String text) {
        lock.lock();
        try {
            appendSingleInternal(source, text);
        } finally {
            lock.unlock();
        }
    }

    private void appendSingleInternal(LogSource source, String text) {
        String sanitized = text != null ? text : "";
        if (sanitized.length() > maxLineLength) {
            int keepLen = Math.max(0, maxLineLength - TRUNCATION_SUFFIX.length());
            sanitized = sanitized.substring(0, keepLen) + TRUNCATION_SUFFIX;
        }

        if (entries.size() >= maxLogLines) {
            entries.pollFirst();
            droppedLinesCount++;
        }

        entries.addLast(BuildLogEntry.builder()
                .timestamp(Instant.now())
                .source(source)
                .message(sanitized)
                .build());
    }

    /**
     * Returns an unmodifiable snapshot list of all stored log entries.
     */
    public List<BuildLogEntry> getEntries() {
        lock.lock();
        try {
            return Collections.unmodifiableList(new ArrayList<>(entries));
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns an unmodifiable snapshot list of formatted log strings.
     */
    public List<String> getFormattedLines() {
        lock.lock();
        try {
            List<String> formatted = new ArrayList<>(entries.size());
            for (BuildLogEntry entry : entries) {
                formatted.add(entry.toFormattedString());
            }
            return Collections.unmodifiableList(formatted);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns all currently buffered logs joined by newlines.
     */
    public String getAllLogsAsString() {
        lock.lock();
        try {
            if (entries.isEmpty()) {
                return "";
            }
            StringBuilder sb = new StringBuilder();
            if (droppedLinesCount > 0) {
                sb.append(String.format("[SYSTEM] ... %d earlier log lines evicted to conserve memory ...%n", droppedLinesCount));
            }
            for (BuildLogEntry entry : entries) {
                sb.append(entry.toFormattedString()).append(System.lineSeparator());
            }
            return sb.toString();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns the current number of retained log entries.
     */
    public int size() {
        lock.lock();
        try {
            return entries.size();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Checks if the buffer is empty.
     */
    public boolean isEmpty() {
        lock.lock();
        try {
            return entries.isEmpty();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns the number of lines evicted due to the circular buffer cap.
     */
    public int getDroppedLinesCount() {
        lock.lock();
        try {
            return droppedLinesCount;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Clears all log entries and resets the dropped lines counter.
     */
    public void clear() {
        lock.lock();
        try {
            entries.clear();
            droppedLinesCount = 0;
        } finally {
            lock.unlock();
        }
    }

    public int getMaxLogLines() {
        return maxLogLines;
    }

    public int getMaxLineLength() {
        return maxLineLength;
    }
}

package com.shipit.backend.build.logging;

/**
 * Indicates the stream origin of a captured build log message.
 */
public enum LogSource {
    STDOUT,
    STDERR,
    SYSTEM
}

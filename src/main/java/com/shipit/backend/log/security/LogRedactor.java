package com.shipit.backend.log.security;

import com.shipit.backend.log.config.LogProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Security utility for redacting sensitive secrets, preventing log injection,
 * and enforcing line size boundaries on deployment logs.
 */
@Component
@RequiredArgsConstructor
public class LogRedactor {

    public static final String REDACTED_MASK = "[REDACTED]";
    private static final String TRUNCATION_SUFFIX = "... [truncated]";

    // GitHub personal access, OAuth, user, server, and refresh tokens
    private static final Pattern GITHUB_TOKEN_PATTERN = Pattern.compile("\\bgh[pousr]_[A-Za-z0-9_]{16,255}\\b");

    // JWT (JSON Web Tokens)
    private static final Pattern JWT_PATTERN = Pattern.compile("\\beyJ[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}\\b");

    // PEM-encoded private keys
    private static final Pattern PRIVATE_KEY_PATTERN = Pattern.compile("-----BEGIN [A-Z ]*PRIVATE KEY-----[\\s\\S]*?-----END [A-Z ]*PRIVATE KEY-----");

    // URLs containing inline basic credentials: http://user:password@hostname
    private static final Pattern URL_CREDENTIAL_PATTERN = Pattern.compile("(https?://[^:\\s@]+):([^@\\s]+)@");

    // Authorization Bearer tokens
    private static final Pattern BEARER_PATTERN = Pattern.compile("(?i)(\\bbearer\\s+)[A-Za-z0-9._~+/-]+=*");

    // Key-value credentials (e.g. password=XYZ, secret: "ABC", token=123)
    private static final Pattern KEY_VALUE_CREDENTIAL_PATTERN = Pattern.compile(
            "(?i)\\b(password|passwd|secret|token|api[_-]?key|access[_-]?key|private[_-]?key)\\b\\s*([:=])\\s*[\"']?(?!\\[REDACTED\\])([^\\s\"',;@]+)[\"']?"
    );

    // ANSI escape sequences
    private static final Pattern ANSI_ESCAPE_PATTERN = Pattern.compile("\\u001B\\[[0-9;]*[a-zA-Z]");

    // Non-printable control characters (excluding tab \t and newline \n)
    private static final Pattern CONTROL_CHARS_PATTERN = Pattern.compile("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]");

    private final LogProperties logProperties;

    /**
     * Sanitizes and redacts a single log message using static pattern matching.
     *
     * @param raw Raw input string.
     * @return Fully redacted, injection-safe, and length-capped log string.
     */
    public String redact(String raw) {
        return redact(raw, null);
    }

    /**
     * Sanitizes and redacts a single log message with both static patterns and dynamic sensitive values.
     *
     * @param raw Raw input string.
     * @param sensitiveValues Known dynamic secrets (e.g. decrypted project environment variable values).
     * @return Fully redacted, injection-safe, and length-capped log string.
     */
    public String redact(String raw, Collection<String> sensitiveValues) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }

        String result = raw;

        // 1. Sanitize CRLF and control characters to prevent log injection
        result = sanitizeControlCharacters(result);

        // 2. Redact dynamic secrets provided from environment variables
        if (sensitiveValues != null && !sensitiveValues.isEmpty()) {
            for (String secret : sensitiveValues) {
                if (secret != null && secret.length() >= 3) {
                    result = result.replace(secret, REDACTED_MASK);
                }
            }
        }

        // 3. Redact static well-known credential patterns
        result = GITHUB_TOKEN_PATTERN.matcher(result).replaceAll(REDACTED_MASK);
        result = JWT_PATTERN.matcher(result).replaceAll(REDACTED_MASK);
        result = PRIVATE_KEY_PATTERN.matcher(result).replaceAll(REDACTED_MASK);

        // Replace basic auth in URLs (preserve username, mask password)
        result = URL_CREDENTIAL_PATTERN.matcher(result).replaceAll("$1:" + REDACTED_MASK + "@");

        // Replace bearer token value
        result = BEARER_PATTERN.matcher(result).replaceAll("$1" + REDACTED_MASK);

        // Replace key=value credentials
        Matcher kvMatcher = KEY_VALUE_CREDENTIAL_PATTERN.matcher(result);
        if (kvMatcher.find()) {
            StringBuffer sb = new StringBuffer();
            do {
                kvMatcher.appendReplacement(sb, kvMatcher.group(1) + kvMatcher.group(2) + REDACTED_MASK);
            } while (kvMatcher.find());
            kvMatcher.appendTail(sb);
            result = sb.toString();
        }

        // 4. Enforce max line length boundary
        int maxLength = logProperties != null ? logProperties.getMaxLineLength() : 2048;
        if (maxLength > 0 && result.length() > maxLength) {
            int keepLen = Math.max(0, maxLength - TRUNCATION_SUFFIX.length());
            result = result.substring(0, keepLen) + TRUNCATION_SUFFIX;
        }

        return result;
    }

    /**
     * Cleans dangerous ANSI escape codes and unprintable control characters.
     */
    public String sanitizeControlCharacters(String input) {
        if (input == null) {
            return "";
        }
        // Normalize CRLF to LF, and strip lone carriage returns
        String normalized = input.replace("\r\n", "\n").replace("\r", "");
        // Strip ANSI escape sequences
        normalized = ANSI_ESCAPE_PATTERN.matcher(normalized).replaceAll("");
        // Strip non-printable control characters (protects against terminal hijacking and log splitting)
        normalized = CONTROL_CHARS_PATTERN.matcher(normalized).replaceAll("");
        return normalized;
    }
}

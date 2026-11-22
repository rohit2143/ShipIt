package com.shipit.backend.log.security;

import com.shipit.backend.log.config.LogProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class LogRedactorTest {

    private LogProperties logProperties;
    private LogRedactor redactor;

    @BeforeEach
    void setUp() {
        logProperties = new LogProperties();
        logProperties.setMaxLineLength(100);
        redactor = new LogRedactor(logProperties);
    }

    @Test
    @DisplayName("redact - Redacts GitHub tokens correctly")
    void redact_GitHubTokens() {
        String input = "Cloning with token ghp_1234567890abcdef1234567890abcdef1234 and ghs_abcdef1234567890abcdef1234567890";
        String redacted = redactor.redact(input);

        assertThat(redacted).doesNotContain("ghp_1234567890abcdef");
        assertThat(redacted).doesNotContain("ghs_abcdef1234567890");
        assertThat(redacted).contains("[REDACTED]");
    }

    @Test
    @DisplayName("redact - Redacts JWT tokens correctly")
    void redact_JwtTokens() {
        String jwt = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIn0.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c";
        String input = "Authorization received: " + jwt;
        String redacted = redactor.redact(input);

        assertThat(redacted).doesNotContain(jwt);
        assertThat(redacted).isEqualTo("Authorization received: [REDACTED]");
    }

    @Test
    @DisplayName("redact - Redacts PEM private keys")
    void redact_PrivateKeys() {
        String key = "-----BEGIN RSA PRIVATE KEY-----\nMIIEowIBAAKCAQEA0Y...\n-----END RSA PRIVATE KEY-----";
        String input = "Loaded SSH key:\n" + key + "\nProceeding.";
        String redacted = redactor.redact(input);

        assertThat(redacted).doesNotContain("MIIEowIBAAKCAQEA0Y");
        assertThat(redacted).contains("[REDACTED]");
    }

    @Test
    @DisplayName("redact - Redacts URL embedded credentials")
    void redact_UrlCredentials() {
        String input = "git clone https://x-access-token:mySecretPassword123@github.com/repo.git";
        String redacted = redactor.redact(input);

        assertThat(redacted).doesNotContain("mySecretPassword123");
        assertThat(redacted).isEqualTo("git clone https://x-access-token:[REDACTED]@github.com/repo.git");
    }

    @Test
    @DisplayName("redact - Redacts Bearer tokens")
    void redact_BearerTokens() {
        String input = "curl -H 'Authorization: Bearer secret_token_xyz_12345' https://api.service.com";
        String redacted = redactor.redact(input);

        assertThat(redacted).doesNotContain("secret_token_xyz_12345");
        assertThat(redacted).contains("Bearer [REDACTED]");
    }

    @Test
    @DisplayName("redact - Redacts key-value sensitive parameters")
    void redact_KeyValueSecrets() {
        String input = "Connecting with password=SuperSecretPass; api_key: \"myApiKey123\"; token=abc987654";
        String redacted = redactor.redact(input);

        assertThat(redacted).doesNotContain("SuperSecretPass");
        assertThat(redacted).doesNotContain("myApiKey123");
        assertThat(redacted).doesNotContain("abc987654");
        assertThat(redacted).contains("password=[REDACTED]");
        assertThat(redacted).contains("api_key:[REDACTED]");
        assertThat(redacted).contains("token=[REDACTED]");
    }

    @Test
    @DisplayName("redact - Redacts dynamic environment variable secrets")
    void redact_DynamicSecrets() {
        String input = "DB connection established to postgres://db:5432 with password my_production_database_password";
        Set<String> secrets = Set.of("my_production_database_password", "stripe_live_secret");

        String redacted = redactor.redact(input, secrets);

        assertThat(redacted).doesNotContain("my_production_database_password");
        assertThat(redacted).isEqualTo("DB connection established to postgres://db:5432 with password [REDACTED]");
    }

    @Test
    @DisplayName("redact - Ignores null, empty, or very short dynamic secrets")
    void redact_DynamicSecrets_ShortOrNullIgnored() {
        String input = "Container listening on port 8080 with dev profile";
        String redacted = redactor.redact(input, List.of("80", ""));

        assertThat(redacted).isEqualTo("Container listening on port 8080 with dev profile");
    }

    @Test
    @DisplayName("redact - Sanitizes ANSI escape color sequences and control characters")
    void redact_AnsiAndControlChars() {
        String input = "\u001B[32m[SUCCESS]\u001B[0m Process finished with code 0\u0007\u0008";
        String redacted = redactor.redact(input);

        assertThat(redacted).doesNotContain("\u001B[32m");
        assertThat(redacted).doesNotContain("\u0007");
        assertThat(redacted).isEqualTo("[SUCCESS] Process finished with code 0");
    }

    @Test
    @DisplayName("redact - Truncates excessively long lines to maxLineLength")
    void redact_Truncation() {
        logProperties.setMaxLineLength(40);
        String veryLongLine = "This is a very long log line that exceeds the forty character limit configured.";

        String redacted = redactor.redact(veryLongLine);

        assertThat(redacted.length()).isEqualTo(40);
        assertThat(redacted).endsWith("... [truncated]");
    }

    @Test
    @DisplayName("redact - Handles null and empty strings safely")
    void redact_NullOrEmpty() {
        assertThat(redactor.redact(null)).isEmpty();
        assertThat(redactor.redact("")).isEmpty();
    }
}

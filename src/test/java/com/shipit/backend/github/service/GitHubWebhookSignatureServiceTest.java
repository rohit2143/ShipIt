package com.shipit.backend.github.service;

import com.shipit.backend.common.exception.AppException;
import com.shipit.backend.config.GitHubAppProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GitHubWebhookSignatureServiceTest {

    private GitHubAppProperties properties;
    private GitHubWebhookSignatureService signatureService;

    @BeforeEach
    void setUp() {
        properties = new GitHubAppProperties();
        properties.setWebhookSecret("my_super_secret_webhook_key_123");
        signatureService = new GitHubWebhookSignatureService(properties);
    }

    @Test
    @DisplayName("Should verify valid signature successfully")
    void shouldVerifyValidSignature() {
        String payload = "{\"ref\":\"refs/heads/main\",\"action\":\"push\"}";
        String expectedHmac = signatureService.computeHmacSha256(payload, "my_super_secret_webhook_key_123");
        String signatureHeader = "sha256=" + expectedHmac;

        assertThatCode(() -> signatureService.verifySignature(payload, signatureHeader))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should throw INVALID_WEBHOOK_SIGNATURE when signature header is missing")
    void shouldThrowWhenSignatureHeaderMissing() {
        String payload = "{\"ref\":\"refs/heads/main\"}";

        assertThatThrownBy(() -> signatureService.verifySignature(payload, null))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Missing X-Hub-Signature-256")
                .extracting("errorCode")
                .isEqualTo("INVALID_WEBHOOK_SIGNATURE");

        assertThatThrownBy(() -> signatureService.verifySignature(payload, ""))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Missing X-Hub-Signature-256")
                .extracting("errorCode")
                .isEqualTo("INVALID_WEBHOOK_SIGNATURE");
    }

    @Test
    @DisplayName("Should throw INVALID_WEBHOOK_SIGNATURE when signature header format is malformed")
    void shouldThrowWhenSignatureMalformed() {
        String payload = "{\"ref\":\"refs/heads/main\"}";

        assertThatThrownBy(() -> signatureService.verifySignature(payload, "invalid_prefix_signature"))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("must start with sha256=")
                .extracting("errorCode")
                .isEqualTo("INVALID_WEBHOOK_SIGNATURE");
    }

    @Test
    @DisplayName("Should throw INVALID_WEBHOOK_SIGNATURE when payload has been tampered with")
    void shouldThrowWhenPayloadTampered() {
        String originalPayload = "{\"ref\":\"refs/heads/main\"}";
        String signatureHeader = "sha256=" + signatureService.computeHmacSha256(originalPayload, "my_super_secret_webhook_key_123");

        String tamperedPayload = "{\"ref\":\"refs/heads/main\",\"extra\":\"malicious\"}";

        assertThatThrownBy(() -> signatureService.verifySignature(tamperedPayload, signatureHeader))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("verification failed")
                .extracting("errorCode")
                .isEqualTo("INVALID_WEBHOOK_SIGNATURE");
    }

    @Test
    @DisplayName("Should throw INVALID_WEBHOOK_SIGNATURE when signature does not match secret")
    void shouldThrowWhenSignatureSignedWithDifferentSecret() {
        String payload = "{\"ref\":\"refs/heads/main\"}";
        String wrongSignature = "sha256=" + signatureService.computeHmacSha256(payload, "different_secret");

        assertThatThrownBy(() -> signatureService.verifySignature(payload, wrongSignature))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("verification failed")
                .extracting("errorCode")
                .isEqualTo("INVALID_WEBHOOK_SIGNATURE");
    }

    @Test
    @DisplayName("Should skip verification gracefully when webhookSecret is blank (development mode)")
    void shouldSkipWhenSecretIsBlank() {
        properties.setWebhookSecret("");

        assertThatCode(() -> signatureService.verifySignature("payload", null))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should compute deterministic SHA-256 payload hash")
    void shouldComputeDeterministicPayloadHash() {
        String payload = "{\"event\":\"test\"}";
        String hash1 = signatureService.computePayloadHash(payload);
        String hash2 = signatureService.computePayloadHash(payload);

        assertThat(hash1).isNotNull();
        assertThat(hash1).isEqualTo(hash2);
        assertThat(hash1).hasSize(64); // hex SHA-256 is 64 characters
    }
}

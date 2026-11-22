package com.shipit.backend.project.service;

import com.shipit.backend.common.exception.BadRequestException;
import com.shipit.backend.config.EncryptionProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EncryptionServiceTest {

    private EncryptionService encryptionService;
    private UUID testProjectId;
    private String testKey;

    @BeforeEach
    void setUp() {
        EncryptionProperties properties = new EncryptionProperties();
        // Standard 64-char hex key (256 bits)
        properties.setSecretKey("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");

        encryptionService = new EncryptionService(properties);
        encryptionService.init();

        testProjectId = UUID.randomUUID();
        testKey = "DATABASE_PASSWORD";
    }

    @Test
    @DisplayName("encrypt and decrypt - Roundtrip produces original plaintext")
    void encryptAndDecrypt_Success() {
        String plaintext = "SuperSecretPassword#123!";

        String ciphertext = encryptionService.encrypt(testProjectId, testKey, plaintext);

        assertThat(ciphertext).isNotNull();
        assertThat(ciphertext).startsWith(EncryptionService.ENVELOPE_PREFIX);
        assertThat(encryptionService.isEncrypted(ciphertext)).isTrue();
        assertThat(ciphertext).isNotEqualTo(plaintext);

        String decrypted = encryptionService.decrypt(testProjectId, testKey, ciphertext);
        assertThat(decrypted).isEqualTo(plaintext);
    }

    @Test
    @DisplayName("encrypt - Random IV ensures identical plaintexts produce distinct ciphertexts")
    void encrypt_ProducesDistinctCiphertextsDueToRandomIV() {
        String plaintext = "ConstantSecretValue";

        String ciphertext1 = encryptionService.encrypt(testProjectId, testKey, plaintext);
        String ciphertext2 = encryptionService.encrypt(testProjectId, testKey, plaintext);

        assertThat(ciphertext1).isNotEqualTo(ciphertext2);
        assertThat(encryptionService.decrypt(testProjectId, testKey, ciphertext1)).isEqualTo(plaintext);
        assertThat(encryptionService.decrypt(testProjectId, testKey, ciphertext2)).isEqualTo(plaintext);
    }

    @Test
    @DisplayName("decrypt - AAD mismatch on wrong project ID fails with DECRYPTION_AUTH_FAILED")
    void decrypt_WrongProjectId_ThrowsBadRequestException() {
        String plaintext = "TenantSecret";
        String ciphertext = encryptionService.encrypt(testProjectId, testKey, plaintext);

        UUID wrongProjectId = UUID.randomUUID();

        assertThatThrownBy(() -> encryptionService.decrypt(wrongProjectId, testKey, ciphertext))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Decryption failed: authentication failed or tampered data");
    }

    @Test
    @DisplayName("decrypt - AAD mismatch on wrong environment variable key fails with DECRYPTION_AUTH_FAILED")
    void decrypt_WrongKey_ThrowsBadRequestException() {
        String plaintext = "ApiKeyValue";
        String ciphertext = encryptionService.encrypt(testProjectId, testKey, plaintext);

        String wrongKey = "DIFFERENT_KEY";

        assertThatThrownBy(() -> encryptionService.decrypt(testProjectId, wrongKey, ciphertext))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Decryption failed: authentication failed or tampered data");
    }

    @Test
    @DisplayName("decrypt - Tampered ciphertext fails authentication")
    void decrypt_TamperedCiphertext_ThrowsBadRequestException() {
        String plaintext = "SecretPayload";
        String ciphertext = encryptionService.encrypt(testProjectId, testKey, plaintext);

        // Tamper with a character in the Base64 payload
        char lastChar = ciphertext.charAt(ciphertext.length() - 1);
        char replacedChar = (lastChar == 'A') ? 'B' : 'A';
        String tamperedCiphertext = ciphertext.substring(0, ciphertext.length() - 1) + replacedChar;

        assertThatThrownBy(() -> encryptionService.decrypt(testProjectId, testKey, tamperedCiphertext))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("decrypt - Missing version prefix throws INVALID_CIPHERTEXT")
    void decrypt_MissingVersionPrefix_ThrowsBadRequestException() {
        assertThatThrownBy(() -> encryptionService.decrypt(testProjectId, testKey, "rawPlaintextWithoutPrefix"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Invalid ciphertext envelope format");
    }

    @Test
    @DisplayName("decrypt - Truncated payload (< 28 bytes) throws CORRUPT_CIPHERTEXT")
    void decrypt_TruncatedPayload_ThrowsBadRequestException() {
        // "v1:" + Base64("short") -> length is only 5 bytes, way below 28 bytes (12 IV + 16 Tag)
        String truncated = "v1:c2hvcnQ=";

        assertThatThrownBy(() -> encryptionService.decrypt(testProjectId, testKey, truncated))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Corrupt ciphertext payload");
    }

    @Test
    @DisplayName("encrypt and decrypt - Empty plaintext string works cleanly")
    void encryptAndDecrypt_EmptyString_Success() {
        String ciphertext = encryptionService.encrypt(testProjectId, testKey, "");
        assertThat(ciphertext).startsWith(EncryptionService.ENVELOPE_PREFIX);

        String decrypted = encryptionService.decrypt(testProjectId, testKey, ciphertext);
        assertThat(decrypted).isEqualTo("");
    }

    @Test
    @DisplayName("encrypt and decrypt - Null values return null")
    void encryptAndDecrypt_NullHandling() {
        assertThat(encryptionService.encrypt(testProjectId, testKey, null)).isNull();
        assertThat(encryptionService.decrypt(testProjectId, testKey, null)).isNull();
    }

    @Test
    @DisplayName("encrypt and decrypt - Multiline, JSON, and special UTF-8 characters work cleanly")
    void encryptAndDecrypt_ComplexPayload() {
        String complexPayload = """
                {
                   "apiKey": "sk-proj-1234567890",
                   "privateKey": "-----BEGIN RSA PRIVATE KEY-----\\nMIIEowIBAAKCAQEA0...\\n-----END RSA PRIVATE KEY-----",
                   "unicode": "🚀 ShipIt 2.0 ✨ — AES-256-GCM"
                }
                """;

        String ciphertext = encryptionService.encrypt(testProjectId, testKey, complexPayload);
        String decrypted = encryptionService.decrypt(testProjectId, testKey, ciphertext);

        assertThat(decrypted).isEqualTo(complexPayload);
    }

    @Test
    @DisplayName("init - Invalid key length throws IllegalArgumentException")
    void init_InvalidKeyLength_ThrowsIllegalArgumentException() {
        EncryptionProperties shortProperties = new EncryptionProperties();
        shortProperties.setSecretKey("tooShortKey123");

        EncryptionService service = new EncryptionService(shortProperties);
        assertThatThrownBy(service::init)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Master encryption key must resolve to exactly 256 bits");
    }
}

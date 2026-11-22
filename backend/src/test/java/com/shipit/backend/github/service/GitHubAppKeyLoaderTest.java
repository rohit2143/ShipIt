package com.shipit.backend.github.service;

import com.shipit.backend.common.exception.AppException;
import com.shipit.backend.config.GitHubAppProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.util.Arrays;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GitHubAppKeyLoaderTest {

    private GitHubAppProperties properties;
    private GitHubAppKeyLoader keyLoader;
    private KeyPair keyPair;
    private String pkcs8Pem;
    private String pkcs1Pem;

    @BeforeEach
    void setUp() throws Exception {
        properties = new GitHubAppProperties();
        keyLoader = new GitHubAppKeyLoader(properties);

        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        keyPair = kpg.generateKeyPair();

        byte[] pkcs8Bytes = keyPair.getPrivate().getEncoded();
        String pkcs8Base64 = Base64.getEncoder().encodeToString(pkcs8Bytes);
        pkcs8Pem = "-----BEGIN PRIVATE KEY-----\n" + pkcs8Base64 + "\n-----END PRIVATE KEY-----";

        // Extract PKCS#1 bytes from PKCS#8 for RSA 2048:
        // PKCS#8 for 2048-bit RSA has standard 26-byte DER header:
        // 30 82 04 bd 02 01 00 30 0d 06 09 2a 86 48 86 f7 0d 01 01 01 05 00 04 82 04 a7
        // Header length is 26 bytes for 2048-bit keys
        byte[] pkcs1Bytes = extractPkcs1FromPkcs8(pkcs8Bytes);
        String pkcs1Base64 = Base64.getEncoder().encodeToString(pkcs1Bytes);
        pkcs1Pem = "-----BEGIN RSA PRIVATE KEY-----\n" + pkcs1Base64 + "\n-----END RSA PRIVATE KEY-----";
    }

    private byte[] extractPkcs1FromPkcs8(byte[] pkcs8Bytes) {
        // Find the octet string tag (0x04) after the AlgorithmIdentifier
        // AlgorithmIdentifier ends with 05 00 (NULL)
        for (int i = 0; i < pkcs8Bytes.length - 2; i++) {
            if (pkcs8Bytes[i] == 0x05 && pkcs8Bytes[i + 1] == 0x00 && pkcs8Bytes[i + 2] == 0x04) {
                int octetTagPos = i + 2;
                int lenByte = pkcs8Bytes[octetTagPos + 1] & 0xff;
                int offset;
                if (lenByte < 0x80) {
                    offset = octetTagPos + 2;
                } else if (lenByte == 0x81) {
                    offset = octetTagPos + 3;
                } else if (lenByte == 0x82) {
                    offset = octetTagPos + 4;
                } else {
                    offset = octetTagPos + 5;
                }
                return Arrays.copyOfRange(pkcs8Bytes, offset, pkcs8Bytes.length);
            }
        }
        throw new IllegalStateException("Failed to extract PKCS#1 bytes from PKCS#8");
    }

    @Test
    @DisplayName("Should successfully load and parse valid PKCS#8 private key from string")
    void shouldLoadValidPkcs8KeyFromString() {
        properties.setPrivateKey(pkcs8Pem);

        RSAPrivateKey loadedKey = keyLoader.loadPrivateKey();

        assertThat(loadedKey).isNotNull();
        assertThat(loadedKey.getModulus()).isEqualTo(((RSAPrivateKey) keyPair.getPrivate()).getModulus());
    }

    @Test
    @DisplayName("Should successfully load and parse valid PKCS#1 private key from string")
    void shouldLoadValidPkcs1KeyFromString() {
        properties.setPrivateKey(pkcs1Pem);

        RSAPrivateKey loadedKey = keyLoader.loadPrivateKey();

        assertThat(loadedKey).isNotNull();
        assertThat(loadedKey.getModulus()).isEqualTo(((RSAPrivateKey) keyPair.getPrivate()).getModulus());
    }

    @Test
    @DisplayName("Should handle PEM string containing literal escaped newlines (\\n)")
    void shouldHandleEscapedNewlines() {
        String escapedPem = pkcs8Pem.replace("\n", "\\n");
        properties.setPrivateKey(escapedPem);

        RSAPrivateKey loadedKey = keyLoader.loadPrivateKey();

        assertThat(loadedKey).isNotNull();
        assertThat(loadedKey.getModulus()).isEqualTo(((RSAPrivateKey) keyPair.getPrivate()).getModulus());
    }

    @Test
    @DisplayName("Should load private key from file path when privateKey is empty")
    void shouldLoadPrivateKeyFromFilePath(@TempDir Path tempDir) throws IOException {
        Path keyFile = tempDir.resolve("github-app-key.pem");
        Files.writeString(keyFile, pkcs8Pem);

        properties.setPrivateKey("");
        properties.setPrivateKeyPath(keyFile.toString());

        RSAPrivateKey loadedKey = keyLoader.loadPrivateKey();

        assertThat(loadedKey).isNotNull();
        assertThat(loadedKey.getModulus()).isEqualTo(((RSAPrivateKey) keyPair.getPrivate()).getModulus());
    }

    @Test
    @DisplayName("Should throw GITHUB_APP_KEY_NOT_CONFIGURED when neither key nor path is provided")
    void shouldThrowWhenNoKeyConfigured() {
        properties.setPrivateKey("");
        properties.setPrivateKeyPath("");

        assertThatThrownBy(() -> keyLoader.loadPrivateKey())
                .isInstanceOf(AppException.class)
                .hasMessageContaining("not configured")
                .extracting("errorCode")
                .isEqualTo("GITHUB_APP_KEY_NOT_CONFIGURED");
    }

    @Test
    @DisplayName("Should throw GITHUB_APP_KEY_FILE_NOT_FOUND when private key file does not exist")
    void shouldThrowWhenFileDoesNotExist() {
        properties.setPrivateKey("");
        properties.setPrivateKeyPath("/path/to/nonexistent/key.pem");

        assertThatThrownBy(() -> keyLoader.loadPrivateKey())
                .isInstanceOf(AppException.class)
                .hasMessageContaining("file not found")
                .extracting("errorCode")
                .isEqualTo("GITHUB_APP_KEY_FILE_NOT_FOUND");
    }

    @Test
    @DisplayName("Should throw INVALID_PRIVATE_KEY_FORMAT when header is not recognized")
    void shouldThrowOnInvalidFormat() {
        String invalidPem = "-----BEGIN CERTIFICATE-----\nsomeData\n-----END CERTIFICATE-----";
        properties.setPrivateKey(invalidPem);

        assertThatThrownBy(() -> keyLoader.loadPrivateKey())
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Unsupported private key format")
                .extracting("errorCode")
                .isEqualTo("INVALID_PRIVATE_KEY_FORMAT");
    }

    @Test
    @DisplayName("Should throw INVALID_PRIVATE_KEY_BASE64 when base64 content is corrupted")
    void shouldThrowOnCorruptedBase64() {
        String corruptedPem = "-----BEGIN PRIVATE KEY-----\nNot Valid Base64 !!!\n-----END PRIVATE KEY-----";
        properties.setPrivateKey(corruptedPem);

        assertThatThrownBy(() -> keyLoader.loadPrivateKey())
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo("INVALID_PRIVATE_KEY_BASE64");
    }
}

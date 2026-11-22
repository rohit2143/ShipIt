package com.shipit.backend.project.service;

import com.shipit.backend.common.exception.BadRequestException;
import com.shipit.backend.config.EncryptionProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class EncryptionService {

    public static final String ENVELOPE_PREFIX = "v1:";
    private static final String ALGORITHM = "AES";
    private static final String CIPHER_TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH_BYTES = 12;
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final int MIN_COMBINED_PAYLOAD_BYTES = GCM_IV_LENGTH_BYTES + (GCM_TAG_LENGTH_BITS / 8); // 12 + 16 = 28

    private final EncryptionProperties encryptionProperties;
    private final SecureRandom secureRandom = new SecureRandom();
    private SecretKey secretKey;

    @PostConstruct
    public void init() {
        this.secretKey = parseSecretKey(encryptionProperties.getSecretKey());
        log.info("Initialized AES-256-GCM encryption service with 256-bit master key");
    }

    /**
     * Encrypts a plaintext secret value at rest using AES-256-GCM with Additional Authenticated Data (AAD)
     * derived from the project ID and environment variable key.
     *
     * @param projectId Project UUID
     * @param key       Environment variable key
     * @param plaintext Plaintext value to encrypt
     * @return Versioned ciphertext envelope format: v1:Base64(IV + Ciphertext + Tag)
     */
    public String encrypt(UUID projectId, String key, String plaintext) {
        if (plaintext == null) {
            return null;
        }

        try {
            byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);

            String aad = buildAad(projectId, key);
            cipher.updateAAD(aad.getBytes(StandardCharsets.UTF_8));

            byte[] cipherTextWithTag = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            byte[] combined = new byte[iv.length + cipherTextWithTag.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(cipherTextWithTag, 0, combined, iv.length, cipherTextWithTag.length);

            return ENVELOPE_PREFIX + Base64.getEncoder().encodeToString(combined);
        } catch (GeneralSecurityException e) {
            log.error("Failed to encrypt secret environment variable for project: {} key: {}", projectId, key, e);
            throw new BadRequestException("Encryption failed: " + e.getMessage(), "ENCRYPTION_FAILED");
        }
    }

    /**
     * Decrypts an encrypted secret environment variable value using AES-256-GCM with AAD validation.
     * This method must only be invoked when required for deployment execution contexts.
     *
     * @param projectId          Project UUID
     * @param key                Environment variable key
     * @param ciphertextEnvelope Versioned ciphertext envelope format (v1:Base64(IV + Ciphertext + Tag))
     * @return Plaintext UTF-8 string
     */
    public String decrypt(UUID projectId, String key, String ciphertextEnvelope) {
        if (ciphertextEnvelope == null) {
            return null;
        }

        if (!isEncrypted(ciphertextEnvelope)) {
            throw new BadRequestException("Invalid ciphertext envelope format: expected 'v1:' prefix", "INVALID_CIPHERTEXT");
        }

        String base64Payload = ciphertextEnvelope.substring(ENVELOPE_PREFIX.length());
        byte[] combined;
        try {
            combined = Base64.getDecoder().decode(base64Payload);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Malformed Base64 in ciphertext envelope", "INVALID_CIPHERTEXT");
        }

        if (combined.length < MIN_COMBINED_PAYLOAD_BYTES) {
            throw new BadRequestException("Corrupt ciphertext payload: payload too short", "CORRUPT_CIPHERTEXT");
        }

        byte[] iv = Arrays.copyOfRange(combined, 0, GCM_IV_LENGTH_BYTES);
        byte[] cipherTextWithTag = Arrays.copyOfRange(combined, GCM_IV_LENGTH_BYTES, combined.length);

        try {
            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);

            String aad = buildAad(projectId, key);
            cipher.updateAAD(aad.getBytes(StandardCharsets.UTF_8));

            byte[] decryptedBytes = cipher.doFinal(cipherTextWithTag);
            return new String(decryptedBytes, StandardCharsets.UTF_8);
        } catch (AEADBadTagException e) {
            log.warn("AEAD authentication tag mismatch when decrypting secret for project: {} key: {}. Possible tampering or key/project mismatch.", projectId, key);
            throw new BadRequestException("Decryption failed: authentication failed or tampered data", "DECRYPTION_AUTH_FAILED");
        } catch (GeneralSecurityException e) {
            log.error("Failed to decrypt secret environment variable for project: {} key: {}", projectId, key, e);
            throw new BadRequestException("Decryption failed: " + e.getMessage(), "DECRYPTION_FAILED");
        }
    }

    /**
     * Checks whether a stored value is in the versioned ciphertext envelope format.
     */
    public boolean isEncrypted(String value) {
        return value != null && value.startsWith(ENVELOPE_PREFIX);
    }

    /**
     * Constructs the Additional Authenticated Data (AAD) string binding the encrypted ciphertext
     * to its specific project ID and variable key.
     */
    public String buildAad(UUID projectId, String key) {
        return (projectId != null ? projectId.toString() : "null") + ":" + key;
    }

    private SecretKey parseSecretKey(String rawKey) {
        if (rawKey == null || rawKey.isBlank()) {
            throw new IllegalStateException("ShipIt master encryption key is not configured");
        }

        byte[] keyBytes;
        String trimmed = rawKey.trim();

        if (trimmed.length() == 64 && trimmed.matches("^[0-9a-fA-F]{64}$")) {
            keyBytes = HexFormat.of().parseHex(trimmed);
        } else {
            try {
                byte[] decoded = Base64.getDecoder().decode(trimmed);
                if (decoded.length == 32) {
                    keyBytes = decoded;
                } else {
                    keyBytes = trimmed.getBytes(StandardCharsets.UTF_8);
                }
            } catch (IllegalArgumentException e) {
                keyBytes = trimmed.getBytes(StandardCharsets.UTF_8);
            }
        }

        if (keyBytes.length != 32) {
            throw new IllegalArgumentException(
                    "Master encryption key must resolve to exactly 256 bits (32 bytes), but got " + keyBytes.length + " bytes"
            );
        }

        return new SecretKeySpec(keyBytes, ALGORITHM);
    }
}

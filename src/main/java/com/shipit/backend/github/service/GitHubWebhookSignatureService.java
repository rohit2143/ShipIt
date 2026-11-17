package com.shipit.backend.github.service;

import com.shipit.backend.common.exception.AppException;
import com.shipit.backend.config.GitHubAppProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Slf4j
@Service
@RequiredArgsConstructor
public class GitHubWebhookSignatureService {

    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final String SIGNATURE_PREFIX = "sha256=";

    private final GitHubAppProperties properties;

    public void verifySignature(String rawPayload, String signatureHeader) {
        String secret = properties.getWebhookSecret();

        if (secret == null || secret.isBlank()) {
            log.warn("GitHub webhook secret is not configured; skipping signature verification");
            return;
        }

        if (signatureHeader == null || signatureHeader.isBlank()) {
            throw new AppException(
                    "Missing X-Hub-Signature-256 header",
                    HttpStatus.UNAUTHORIZED,
                    "INVALID_WEBHOOK_SIGNATURE"
            );
        }

        if (!signatureHeader.startsWith(SIGNATURE_PREFIX)) {
            throw new AppException(
                    "Malformed X-Hub-Signature-256 header; must start with sha256=",
                    HttpStatus.UNAUTHORIZED,
                    "INVALID_WEBHOOK_SIGNATURE"
            );
        }

        String expectedSignatureHex = computeHmacSha256(rawPayload, secret);
        String actualSignatureHex = signatureHeader.substring(SIGNATURE_PREFIX.length()).trim();

        byte[] expectedBytes = expectedSignatureHex.getBytes(StandardCharsets.UTF_8);
        byte[] actualBytes = actualSignatureHex.getBytes(StandardCharsets.UTF_8);

        if (!MessageDigest.isEqual(expectedBytes, actualBytes)) {
            log.warn("GitHub webhook signature mismatch");
            throw new AppException(
                    "GitHub webhook signature verification failed",
                    HttpStatus.UNAUTHORIZED,
                    "INVALID_WEBHOOK_SIGNATURE"
            );
        }
    }

    public String computeHmacSha256(String data, String secret) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            SecretKeySpec secretKeySpec =
                    new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256);
            mac.init(secretKeySpec);
            byte[] rawHmac = mac.doFinal((data != null ? data : "").getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(rawHmac);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            log.error("Failed to compute HMAC-SHA256: {}", e.getMessage());
            throw new AppException(
                    "Failed to compute HMAC-SHA256 signature",
                    e,
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "SIGNATURE_COMPUTATION_ERROR"
            );
        }
    }

    public String computePayloadHash(String payload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((payload != null ? payload : "").getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new AppException(
                    "Failed to compute SHA-256 payload hash",
                    e,
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "PAYLOAD_HASH_ERROR"
            );
        }
    }
}

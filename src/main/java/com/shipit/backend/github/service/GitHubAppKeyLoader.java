package com.shipit.backend.github.service;

import com.shipit.backend.common.exception.AppException;
import com.shipit.backend.config.GitHubAppProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

@Slf4j
@Component
public class GitHubAppKeyLoader {

    private final GitHubAppProperties properties;

    public GitHubAppKeyLoader(GitHubAppProperties properties) {
        this.properties = properties;
    }

    public RSAPrivateKey loadPrivateKey() {
        String pem = properties.getPrivateKey();

        if ((pem == null || pem.isBlank()) && properties.getPrivateKeyPath() != null && !properties.getPrivateKeyPath().isBlank()) {
            try {
                Path path = resolveKeyPath(properties.getPrivateKeyPath());
                if (Files.exists(path)) {
                    pem = Files.readString(path);
                } else {
                    throw new AppException(
                            "GitHub App private key file not found: " + properties.getPrivateKeyPath(),
                            HttpStatus.INTERNAL_SERVER_ERROR,
                            "GITHUB_APP_KEY_FILE_NOT_FOUND"
                    );
                }
            } catch (IOException e) {
                throw new AppException(
                        "Failed to read GitHub App private key file: " + e.getMessage(),
                        e,
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "GITHUB_APP_KEY_READ_ERROR"
                );
            }
        }

        if (pem == null || pem.isBlank()) {
            throw new AppException(
                    "GitHub App private key is not configured (set shipit.github.app.private-key or private-key-path)",
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "GITHUB_APP_KEY_NOT_CONFIGURED"
            );
        }

        return parsePrivateKey(pem);
    }

    public RSAPrivateKey parsePrivateKey(String pemContent) {
        if (pemContent == null || pemContent.isBlank()) {
            throw new AppException(
                    "Private key content cannot be empty",
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "INVALID_PRIVATE_KEY"
            );
        }

        String normalized = pemContent.replace("\\n", "\n").trim();

        boolean isPkcs1 = normalized.contains("BEGIN RSA PRIVATE KEY");
        boolean isPkcs8 = normalized.contains("BEGIN PRIVATE KEY");

        if (!isPkcs1 && !isPkcs8) {
            throw new AppException(
                    "Unsupported private key format. Must be PKCS#1 (BEGIN RSA PRIVATE KEY) or PKCS#8 (BEGIN PRIVATE KEY)",
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "INVALID_PRIVATE_KEY_FORMAT"
            );
        }

        String cleaned = normalized
                .replace("-----BEGIN RSA PRIVATE KEY-----", "")
                .replace("-----END RSA PRIVATE KEY-----", "")
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s+", "");

        byte[] derBytes;
        try {
            derBytes = Base64.getDecoder().decode(cleaned);
        } catch (IllegalArgumentException e) {
            throw new AppException(
                    "Failed to base64 decode private key: " + e.getMessage(),
                    e,
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "INVALID_PRIVATE_KEY_BASE64"
            );
        }

        try {
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            if (isPkcs8) {
                PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(derBytes);
                return (RSAPrivateKey) keyFactory.generatePrivate(keySpec);
            } else {
                return wrapAndGeneratePkcs1(derBytes, keyFactory);
            }
        } catch (Exception e) {
            log.error("Failed to parse RSA private key: {}", e.getMessage());
            throw new AppException(
                    "Failed to parse RSA private key: " + e.getMessage(),
                    e,
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "INVALID_RSA_PRIVATE_KEY"
            );
        }
    }

    private RSAPrivateKey wrapAndGeneratePkcs1(byte[] pkcs1Bytes, KeyFactory keyFactory) throws Exception {
        // AlgorithmIdentifier for rsaEncryption (1.2.840.113549.1.1.1):
        // SEQUENCE { OBJECT IDENTIFIER rsaEncryption, NULL }
        byte[] algId = new byte[] {
                0x30, 0x0d, 0x06, 0x09, 0x2a, (byte) 0x86, 0x48, (byte) 0x86,
                (byte) 0xf7, 0x0d, 0x01, 0x01, 0x01, 0x05, 0x00
        };

        ByteArrayOutputStream octetStream = new ByteArrayOutputStream();
        octetStream.write(0x04); // OCTET STRING
        writeDerLength(octetStream, pkcs1Bytes.length);
        octetStream.write(pkcs1Bytes);

        ByteArrayOutputStream seqStream = new ByteArrayOutputStream();
        // INTEGER 0 (version)
        seqStream.write(new byte[] { 0x02, 0x01, 0x00 });
        seqStream.write(algId);
        seqStream.write(octetStream.toByteArray());

        ByteArrayOutputStream pkcs8Stream = new ByteArrayOutputStream();
        pkcs8Stream.write(0x30); // SEQUENCE
        writeDerLength(pkcs8Stream, seqStream.size());
        seqStream.writeTo(pkcs8Stream);

        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(pkcs8Stream.toByteArray());
        return (RSAPrivateKey) keyFactory.generatePrivate(keySpec);
    }

    private void writeDerLength(OutputStream out, int length) throws IOException {
        if (length < 128) {
            out.write(length);
        } else if (length < 256) {
            out.write(0x81);
            out.write(length);
        } else if (length < 65536) {
            out.write(0x82);
            out.write((length >> 8) & 0xff);
            out.write(length & 0xff);
        } else {
            out.write(0x83);
            out.write((length >> 16) & 0xff);
            out.write((length >> 8) & 0xff);
            out.write(length & 0xff);
        }
    }

    private Path resolveKeyPath(String rawPath) {
        Path direct = Paths.get(rawPath);
        if (Files.exists(direct)) {
            return direct;
        }
        Path backendPath = Paths.get("backend", rawPath);
        if (Files.exists(backendPath)) {
            return backendPath;
        }
        Path parentPath = Paths.get("..", rawPath);
        if (Files.exists(parentPath)) {
            return parentPath;
        }
        return direct;
    }
}

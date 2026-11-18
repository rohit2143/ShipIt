package com.shipit.backend.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "shipit.encryption")
public class EncryptionProperties {

    /**
     * Master encryption key (256-bit AES key in 64-char hex, 44-char base64, or 32-char utf8).
     * Must be supplied via environment variable SHIPIT_ENCRYPTION_KEY in production.
     * Never stored in PostgreSQL database.
     */
    private String secretKey = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
}

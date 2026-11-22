package com.shipit.backend.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HashUtilTest {

    @Test
    @DisplayName("Should hash string to 64-character lowercase hex using SHA-256")
    void shouldHashToSha256() {
        String token = "shipit_refresh_sample_token_xyz";
        String hash1 = HashUtil.sha256(token);
        String hash2 = HashUtil.sha256(token);

        assertThat(hash1).isNotNull();
        assertThat(hash1).hasSize(64);
        assertThat(hash1).isEqualTo(hash2);
        assertThat(hash1).matches("^[a-f0-9]{64}$");
    }

    @Test
    @DisplayName("Should produce different hashes for different inputs")
    void shouldProduceDifferentHashes() {
        String hash1 = HashUtil.sha256("token_alpha");
        String hash2 = HashUtil.sha256("token_beta");

        assertThat(hash1).isNotEqualTo(hash2);
    }

    @Test
    @DisplayName("Should return null for null input")
    void shouldHandleNull() {
        assertThat(HashUtil.sha256(null)).isNull();
    }
}

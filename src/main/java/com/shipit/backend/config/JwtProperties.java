package com.shipit.backend.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "shipit.jwt")
public class JwtProperties {

    // Default 256-bit secret for local development/fallback
    private String secret = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";

    // 15 minutes in seconds
    private long accessTokenExpirationSeconds = 900;

    // 7 days in seconds
    private long refreshTokenExpirationSeconds = 604800;
}

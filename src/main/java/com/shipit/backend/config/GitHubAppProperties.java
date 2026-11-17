package com.shipit.backend.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "shipit.github.app")
public class GitHubAppProperties {

    private String appName = "shipit-app";
    private String apiBaseUrl = "https://api.github.com";
    private String appId = "";
    private String clientId = "";
    private String clientSecret = "";
    private String redirectUri = "http://localhost:5173/github/callback";
    private long stateExpirationSeconds = 900;
    private String privateKey = "";
    private String privateKeyPath = "";
    private long tokenBufferSeconds = 300;
    private String webhookSecret = "";
}

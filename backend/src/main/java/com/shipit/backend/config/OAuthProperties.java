package com.shipit.backend.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "shipit.oauth")
public class OAuthProperties {

    private GitHub github = new GitHub();
    private Google google = new Google();

    @Getter
    @Setter
    public static class GitHub {
        private String clientId = "";
        private String clientSecret = "";
        private String redirectUri = "http://localhost:5173/auth/callback/github";
    }

    @Getter
    @Setter
    public static class Google {
        private String clientId = "";
        private String clientSecret = "";
        private String redirectUri = "http://localhost:5173/auth/callback/google";
    }
}

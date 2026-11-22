package com.shipit.backend.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "shipit")
public class ShipitProperties {

    private Cors cors = new Cors();
    private App app = new App();

    @Getter
    @Setter
    public static class Cors {
        private List<String> allowedOrigins = new ArrayList<>(List.of("http://localhost:3000", "http://localhost:5173"));
        private List<String> allowedMethods = new ArrayList<>(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        private List<String> allowedHeaders = new ArrayList<>(List.of("*"));
        private boolean allowCredentials = true;
        private long maxAge = 3600;
    }

    @Getter
    @Setter
    public static class App {
        private String name = "ShipIt";
        private String baseUrl = "http://localhost:8080";
    }
}

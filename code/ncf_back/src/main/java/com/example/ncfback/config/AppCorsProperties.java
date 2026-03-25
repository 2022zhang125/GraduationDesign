package com.example.ncfback.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@Data
@ConfigurationProperties(prefix = "app.cors")
public class AppCorsProperties {
    private List<String> allowedOriginPatterns = new ArrayList<>(List.of(
            "https://*.believesun.cn",
            "http://localhost:*",
            "http://127.0.0.1:*",
            "https://localhost:*",
            "https://127.0.0.1:*"
    ));
    private List<String> allowedMethods = new ArrayList<>(List.of(
            "GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"
    ));
    private List<String> allowedHeaders = new ArrayList<>(List.of("*"));
    private List<String> exposedHeaders = new ArrayList<>(List.of(
            "Authorization", "Content-Disposition", "Content-Length", "Content-Range", "Accept-Ranges", "Location"
    ));
    private boolean allowCredentials = true;
    private Long maxAgeSeconds = 3600L;
}

package com.example.ncfback.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Data
@ConfigurationProperties(prefix = "app.cache")
public class AppCacheProperties {
    private String keyPrefix = "ncf";
    private Duration favoritesTtl = Duration.ofMinutes(30);
    private Duration recommendationsTtl = Duration.ofHours(6);
    private Duration delayedDoubleDelete = Duration.ofMillis(500);
}

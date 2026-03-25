package com.example.ncfback.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.music.catalog")
public class MusicCatalogProperties {
    private boolean autoSyncOnStartup = true;
    private String resourcePath = "classpath:music/real_music_catalog.txt";
}

package com.example.ncfback.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.music.favorite-seed")
public class FavoriteSeedProperties {
    private boolean enabled = true;
    private int minimumPerUser = 4;
}

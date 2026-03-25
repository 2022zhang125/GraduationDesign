package com.example.ncfback.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.music.yaohu")
public class YaohuMusicApiProperties {
    private String key;
    private long refreshHours = 6;
    private int defaultSearchCount = 15;
    private String defaultLevel = "standard";
    private String mediaProxyHost;
    private Integer mediaProxyPort;
    private Provider wyvip = new Provider();
    private Provider qqPlus = new Provider();

    @Data
    public static class Provider {
        private String baseUrl;
    }
}

package com.example.ncfback.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.recommendation")
public class RecommendationRefreshProperties {
    private boolean refreshOnStartup = true;
    private int topK = 10;
    private int candidatePoolSize = 40;
    private int freshnessWindowDays = 7;
    private double dailyExplorationJitter = 0.035d;
    private double repeatedItemPenalty = 0.08d;
    private double repeatedArtistPenalty = 0.07d;
    private double repeatedGenrePenalty = 0.03d;
    private String scene = "home";
    private String modelVersion = "ncf_attention_v2_20260314";
    private String modelName = "NCF Attention Online v2";
    private String featureVersion = "feature_v2";
    private String samplingVersion = "v2";
}

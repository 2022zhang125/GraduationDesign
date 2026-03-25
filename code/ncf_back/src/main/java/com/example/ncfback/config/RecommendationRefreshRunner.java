package com.example.ncfback.config;

import com.example.ncfback.service.RecommendationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(3)
@RequiredArgsConstructor
@Slf4j
public class RecommendationRefreshRunner implements ApplicationRunner {

    private final RecommendationRefreshProperties properties;
    private final RecommendationService recommendationService;

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isRefreshOnStartup()) {
            return;
        }
        try {
            RecommendationService.RefreshSummary summary = recommendationService.refreshAllSnapshots();
            log.info("Refreshed personalized recommendations: users={}, recommendations={}, modelVersion={}",
                    summary.userCount(), summary.recommendationCount(), summary.modelVersion());
        } catch (Exception ex) {
            log.warn("Skip personalized recommendation refresh: {}", ex.getMessage());
        }
    }
}

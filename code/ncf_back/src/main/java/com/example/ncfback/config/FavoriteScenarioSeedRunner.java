package com.example.ncfback.config;

import com.example.ncfback.service.FavoriteScenarioSeedService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Order(2)
@RequiredArgsConstructor
public class FavoriteScenarioSeedRunner implements ApplicationRunner {

    private final FavoriteSeedProperties properties;
    private final FavoriteScenarioSeedService favoriteScenarioSeedService;

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isEnabled()) {
            return;
        }
        int inserted = favoriteScenarioSeedService.seedDefaults();
        if (inserted > 0) {
            log.info("Seeded {} default favorite rows for demo users", inserted);
        }
    }
}

package com.example.ncfback.config;

import com.example.ncfback.service.MusicCatalogSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class MusicCatalogSyncRunner implements ApplicationRunner {

    private final MusicCatalogProperties properties;
    private final MusicCatalogSyncService musicCatalogSyncService;

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isAutoSyncOnStartup()) {
            return;
        }

        try {
            if (!musicCatalogSyncService.needsSync()) {
                return;
            }
            var summary = musicCatalogSyncService.syncAll();
            log.info("Real music catalog sync finished: synced {} of {} items with {} query attempts",
                    summary.syncedItems(), summary.totalItems(), summary.attemptedQueries());
        } catch (Exception ex) {
            log.warn("Skip real music catalog sync: {}", ex.getMessage());
        }
    }
}

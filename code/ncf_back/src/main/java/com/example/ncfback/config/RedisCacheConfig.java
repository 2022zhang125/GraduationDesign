package com.example.ncfback.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
public class RedisCacheConfig {

    @Bean
    public TaskScheduler cacheTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("cache-delay-delete-");
        scheduler.setDaemon(true);
        scheduler.initialize();
        return scheduler;
    }
}

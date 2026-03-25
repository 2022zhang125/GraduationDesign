package com.example.ncfback.service;

import com.example.ncfback.config.AppCacheProperties;
import com.example.ncfback.entity.FavoriteItemView;
import com.example.ncfback.entity.RecommendationView;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserCacheService {

    private static final ZoneId APP_ZONE_ID = ZoneId.of("Asia/Shanghai");

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final AppCacheProperties properties;
    private final TaskScheduler cacheTaskScheduler;

    public List<FavoriteItemView> getFavoriteItems(Long userId, Supplier<List<FavoriteItemView>> loader) {
        return getList(
                favoriteKey(userId),
                favoritesListType(),
                properties.getFavoritesTtl(),
                loader
        );
    }

    public List<RecommendationView> getDailyRecommendations(Long userId, Supplier<List<RecommendationView>> loader) {
        return getList(
                recommendationKey(userId),
                recommendationsListType(),
                properties.getRecommendationsTtl(),
                loader
        );
    }

    public void delayedDoubleDeleteFavorites(Long userId) {
        delayedDoubleDeleteKey(favoriteKey(userId));
    }

    public void delayedDoubleDeleteRecommendations(Long userId) {
        delayedDoubleDeleteKey(recommendationKey(userId));
    }

    public void delayedDoubleDeleteAllRecommendations() {
        delayedDoubleDeletePrefix(recommendationPrefix());
    }

    private <T> List<T> getList(String key,
                                JavaType javaType,
                                Duration ttl,
                                Supplier<List<T>> loader) {
        List<T> cached = readList(key, javaType);
        if (cached != null) {
            return cached;
        }

        List<T> loaded = loader.get();
        writeList(key, loaded, ttl);
        return loaded;
    }

    private <T> List<T> readList(String key, JavaType javaType) {
        try {
            String cachedJson = stringRedisTemplate.opsForValue().get(key);
            if (cachedJson == null || cachedJson.isBlank()) {
                return null;
            }
            List<T> result = objectMapper.readValue(cachedJson, javaType);
            return result == null ? Collections.emptyList() : result;
        } catch (Exception ex) {
            log.warn("Read redis cache failed for key={}: {}", key, ex.getMessage());
            return null;
        }
    }

    private void writeList(String key, Object value, Duration ttl) {
        try {
            String payload = objectMapper.writeValueAsString(value == null ? Collections.emptyList() : value);
            stringRedisTemplate.opsForValue().set(key, payload, ttl);
        } catch (JsonProcessingException ex) {
            log.warn("Serialize redis cache payload failed for key={}: {}", key, ex.getMessage());
        } catch (Exception ex) {
            log.warn("Write redis cache failed for key={}: {}", key, ex.getMessage());
        }
    }

    private void delayedDoubleDeleteKey(String key) {
        deleteKey(key);
        Runnable secondDelete = () -> scheduleDelete(() -> deleteKey(key));
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    secondDelete.run();
                }
            });
            return;
        }
        secondDelete.run();
    }

    private void delayedDoubleDeletePrefix(String keyPrefix) {
        deleteByPrefix(keyPrefix);
        Runnable secondDelete = () -> scheduleDelete(() -> deleteByPrefix(keyPrefix));
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    secondDelete.run();
                }
            });
            return;
        }
        secondDelete.run();
    }

    private void scheduleDelete(Runnable deleteTask) {
        Instant executeAt = Instant.now().plus(properties.getDelayedDoubleDelete());
        cacheTaskScheduler.schedule(deleteTask, executeAt);
    }

    private void deleteKey(String key) {
        try {
            stringRedisTemplate.delete(key);
        } catch (Exception ex) {
            log.warn("Delete redis cache failed for key={}: {}", key, ex.getMessage());
        }
    }

    private void deleteByPrefix(String keyPrefix) {
        try {
            stringRedisTemplate.execute((RedisCallback<Void>) connection -> {
                ScanOptions options = ScanOptions.scanOptions()
                        .match(keyPrefix + "*")
                        .count(100)
                        .build();
                try (Cursor<byte[]> cursor = connection.scan(options)) {
                    while (cursor.hasNext()) {
                        connection.del(Objects.requireNonNull(cursor.next()));
                    }
                }
                return null;
            });
        } catch (Exception ex) {
            log.warn("Delete redis cache by prefix failed for prefix={}: {}", keyPrefix, ex.getMessage());
        }
    }

    private String favoriteKey(Long userId) {
        return properties.getKeyPrefix() + ":favorites:user:" + userId;
    }

    private String recommendationKey(Long userId) {
        return recommendationPrefix() + userId + ":" + LocalDate.now(APP_ZONE_ID);
    }

    private String recommendationPrefix() {
        return properties.getKeyPrefix() + ":recommendations:daily:user:";
    }

    private JavaType favoritesListType() {
        return objectMapper.getTypeFactory().constructCollectionType(List.class, FavoriteItemView.class);
    }

    private JavaType recommendationsListType() {
        return objectMapper.getTypeFactory().constructCollectionType(List.class, RecommendationView.class);
    }
}

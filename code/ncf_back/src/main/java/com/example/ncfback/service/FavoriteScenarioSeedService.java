package com.example.ncfback.service;

import com.example.ncfback.config.FavoriteSeedProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class FavoriteScenarioSeedService {

    private static final Pattern DEMO_USER_PATTERN = Pattern.compile("^U\\d{6}$");

    private final JdbcTemplate jdbcTemplate;
    private final FavoriteSeedProperties properties;

    public int seedDefaults() {
        List<Long> itemIds = jdbcTemplate.queryForList("""
                SELECT item_id
                FROM items
                WHERE item_status = 1
                ORDER BY item_id
                """, Long.class);
        if (itemIds.isEmpty()) {
            return 0;
        }

        List<Map<String, Object>> users = jdbcTemplate.queryForList("""
                SELECT user_id, external_user_no
                FROM users
                WHERE user_status = 1
                ORDER BY user_id
                """);

        int inserted = 0;
        for (Map<String, Object> row : users) {
            Long userId = ((Number) row.get("user_id")).longValue();
            String username = Objects.toString(row.get("external_user_no"), "");
            if (userId == 1L || !isDemoUser(username)) {
                continue;
            }
            Integer currentCount = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*)
                    FROM user_favorite_items
                    WHERE user_id = ?
                    """, Integer.class, userId);
            int missing = properties.getMinimumPerUser() - (currentCount == null ? 0 : currentCount);
            if (missing <= 0) {
                continue;
            }

            Set<Long> candidateItemIds = new LinkedHashSet<>();
            long startIndex = (userId * 3) % itemIds.size();
            for (int offset = 0; candidateItemIds.size() < properties.getMinimumPerUser() && offset < itemIds.size() * 2; offset++) {
                int index = (int) ((startIndex + (long) offset * 7) % itemIds.size());
                candidateItemIds.add(itemIds.get(index));
            }

            for (Long itemId : candidateItemIds) {
                if (missing <= 0) {
                    break;
                }
                inserted += jdbcTemplate.update("""
                        INSERT IGNORE INTO user_favorite_items (user_id, item_id, created_at)
                        VALUES (?, ?, NOW())
                        """, userId, itemId);
                missing = properties.getMinimumPerUser() - jdbcTemplate.queryForObject("""
                        SELECT COUNT(*)
                        FROM user_favorite_items
                        WHERE user_id = ?
                        """, Integer.class, userId);
            }
        }
        return inserted;
    }

    private boolean isDemoUser(String username) {
        if (username == null || username.isBlank()) {
            return false;
        }
        return DEMO_USER_PATTERN.matcher(username).matches() || username.startsWith("music_fan_");
    }
}

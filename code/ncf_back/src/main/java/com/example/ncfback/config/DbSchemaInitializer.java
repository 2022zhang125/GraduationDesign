package com.example.ncfback.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class DbSchemaInitializer implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args) {
        createUsersTableIfMissing();
        migrateUsersTable();
        migrateInteractionsTable();
        createItemMediaTableIfMissing();
        createUserFavoriteItemsTableIfMissing();
        createUserFollowRelationsTableIfMissing();
        createUserFeedbackTicketsTableIfMissing();
        createIndexesIfMissing();
        syncTableComments();
    }

    private void createUsersTableIfMissing() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS users (
                    user_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'User ID',
                    external_user_no VARCHAR(64) NOT NULL COMMENT 'Username',
                    password_hash VARCHAR(255) NULL COMMENT 'BCrypt password hash',
                    gender TINYINT NULL COMMENT 'Gender: 0 unknown, 1 male, 2 female',
                    birth_year SMALLINT NULL COMMENT 'Birth year',
                    register_time DATETIME NOT NULL COMMENT 'Register time',
                    user_status TINYINT NOT NULL DEFAULT 1 COMMENT 'User status: 1 active, 0 disabled',
                    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Create time',
                    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Update time',
                    PRIMARY KEY (user_id),
                    UNIQUE KEY uk_users_external_user_no (external_user_no),
                    KEY idx_users_status (user_status),
                    KEY idx_users_register_time (register_time)
                ) COMMENT='User master table (account and profile basics)'
                """);
    }

    private void migrateUsersTable() {
        backfillMissingUsernames();
        addColumnIfMissing("users", "password_hash",
                "ALTER TABLE users ADD COLUMN password_hash VARCHAR(255) NULL COMMENT 'BCrypt password hash' AFTER external_user_no");
        tryExecute("ALTER TABLE users MODIFY COLUMN user_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'User ID'");
        tryExecute("ALTER TABLE users MODIFY COLUMN external_user_no VARCHAR(64) NOT NULL COMMENT 'Username'");
        tryExecute("ALTER TABLE users MODIFY COLUMN gender TINYINT NULL COMMENT 'Gender: 0 unknown, 1 male, 2 female'");
        tryExecute("ALTER TABLE users MODIFY COLUMN birth_year SMALLINT NULL COMMENT 'Birth year'");
        ensureIndexIfMissing("users", "uk_users_external_user_no",
                "CREATE UNIQUE INDEX uk_users_external_user_no ON users (external_user_no)");
        dropColumnIfPresent("users", "country_code");
        backfillMissingPasswordHashes();
    }

    private void createItemMediaTableIfMissing() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS item_media (
                    item_id BIGINT NOT NULL COMMENT 'Item ID',
                    preview_url VARCHAR(512) NOT NULL COMMENT 'Preview audio URL',
                    cover_url VARCHAR(512) NULL COMMENT 'Cover image URL',
                    preview_duration_seconds INT DEFAULT 30 COMMENT 'Preview duration seconds',
                    lyric_snippet VARCHAR(1000) NULL COMMENT 'Lyric snippet',
                    source_platform VARCHAR(64) NULL COMMENT 'Media source platform',
                    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Update time',
                    PRIMARY KEY (item_id)
                ) COMMENT='Item media extension table (preview, cover and lyric snippet)'
                """);
    }

    private void createUserFavoriteItemsTableIfMissing() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS user_favorite_items (
                    user_id BIGINT NOT NULL COMMENT 'User ID',
                    item_id BIGINT NOT NULL COMMENT 'Item ID',
                    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Favorite create time',
                    PRIMARY KEY (user_id, item_id),
                    KEY idx_favorite_item (item_id),
                    KEY idx_favorite_created_at (created_at)
                ) COMMENT='User favorite item relation table'
                """);
    }

    private void createUserFollowRelationsTableIfMissing() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS user_follow_relations (
                    follower_user_id BIGINT NOT NULL COMMENT 'Follower user ID',
                    followee_user_id BIGINT NOT NULL COMMENT 'Followee user ID',
                    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Follow create time',
                    PRIMARY KEY (follower_user_id, followee_user_id),
                    KEY idx_followee_user (followee_user_id),
                    KEY idx_follow_created_at (created_at)
                ) COMMENT='User social follow relation table'
                """);
    }

    private void createUserFeedbackTicketsTableIfMissing() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS user_feedback_tickets (
                    ticket_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Feedback ticket ID',
                    user_id BIGINT NOT NULL COMMENT 'User ID',
                    prefer_dimension VARCHAR(32) NULL COMMENT 'Preferred music dimension: GENRE/ARTIST/LANGUAGE',
                    prefer_value VARCHAR(128) NULL COMMENT 'Preferred music value',
                    avoid_dimension VARCHAR(32) NULL COMMENT 'Avoided music dimension: GENRE/ARTIST/LANGUAGE',
                    avoid_value VARCHAR(128) NULL COMMENT 'Avoided music value',
                    preference_strength TINYINT NOT NULL DEFAULT 3 COMMENT 'Feedback strength from 1 to 5',
                    detail_text VARCHAR(500) NULL COMMENT 'User feedback detail text',
                    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'Ticket status: ACTIVE/CLOSED',
                    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Create time',
                    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Update time',
                    PRIMARY KEY (ticket_id),
                    KEY idx_feedback_user_status (user_id, status),
                    KEY idx_feedback_created_at (created_at)
                ) COMMENT='User feedback tickets for recommendation preference adjustment'
                """);
    }

    private void migrateInteractionsTable() {
        if (!columnExists("interactions", "event_type")) {
            return;
        }
        tryExecute("""
                ALTER TABLE interactions
                MODIFY COLUMN event_type VARCHAR(32) NOT NULL
                COMMENT 'Interaction event type: play_start, play_stop, play_complete, click, favorite'
                """);
    }

    private void createIndexesIfMissing() {
        try {
            jdbcTemplate.execute("CREATE INDEX idx_rec_user_request_rank ON rec_results (user_id, request_time, rank_no)");
        } catch (Exception ex) {
            log.debug("Skip idx_rec_user_request_rank creation: {}", ex.getMessage());
        }
        try {
            jdbcTemplate.execute("CREATE INDEX idx_item_feature_date_item ON item_features_daily (feature_date, item_id)");
        } catch (Exception ex) {
            log.debug("Skip idx_item_feature_date_item creation: {}", ex.getMessage());
        }
        try {
            jdbcTemplate.execute("CREATE INDEX idx_interactions_user_item_event_time ON interactions (user_id, item_id, event_time)");
        } catch (Exception ex) {
            log.debug("Skip idx_interactions_user_item_event_time creation: {}", ex.getMessage());
        }
    }

    private void syncTableComments() {
        tryExecute("ALTER TABLE users COMMENT='User master table (account and profile basics)'");
        tryExecute("ALTER TABLE items COMMENT='Music item master table (song/artist/album basics)'");
        tryExecute("ALTER TABLE interactions COMMENT='User-item interaction fact table (play/click/favorite)'");
        tryExecute("ALTER TABLE training_samples COMMENT='NCF model training samples'");
        tryExecute("ALTER TABLE rec_results COMMENT='Recommendation result snapshots by request time'");
        tryExecute("ALTER TABLE item_features_daily COMMENT='Daily aggregated item features'");
        tryExecute("ALTER TABLE item_media COMMENT='Item media extension table (preview url, cover, lyric snippet)'");
        tryExecute("ALTER TABLE user_favorite_items COMMENT='User favorite item relation table'");
        tryExecute("ALTER TABLE user_follow_relations COMMENT='User social follow relation table'");
        tryExecute("ALTER TABLE user_feedback_tickets COMMENT='User feedback tickets for recommendation preference adjustment'");
    }

    private void backfillMissingUsernames() {
        try {
            jdbcTemplate.update("""
                    UPDATE users
                    SET external_user_no = CONCAT('user_', user_id),
                        updated_at = NOW()
                    WHERE external_user_no IS NULL OR TRIM(external_user_no) = ''
                    """);
        } catch (Exception ex) {
            log.debug("Skip users username backfill: {}", ex.getMessage());
        }
    }

    private void backfillMissingPasswordHashes() {
        if (!columnExists("users", "password_hash")) {
            return;
        }
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                    SELECT user_id, external_user_no, birth_year
                    FROM users
                    WHERE password_hash IS NULL OR TRIM(password_hash) = ''
                    """);
            for (Map<String, Object> row : rows) {
                Long userId = ((Number) row.get("user_id")).longValue();
                String rawPassword = bootstrapPassword(row.get("birth_year"));
                jdbcTemplate.update(
                        "UPDATE users SET password_hash = ?, updated_at = ? WHERE user_id = ?",
                        passwordEncoder.encode(rawPassword),
                        LocalDateTime.now(),
                        userId
                );
            }
        } catch (Exception ex) {
            log.debug("Skip password hash backfill: {}", ex.getMessage());
        }
    }

    private String bootstrapPassword(Object birthYear) {
        if (birthYear instanceof Number number && number.intValue() > 0) {
            return String.valueOf(number.intValue());
        }
        return "123456";
    }

    private void addColumnIfMissing(String tableName, String columnName, String sql) {
        if (!columnExists(tableName, columnName)) {
            tryExecute(sql);
        }
    }

    private void dropColumnIfPresent(String tableName, String columnName) {
        if (columnExists(tableName, columnName)) {
            tryExecute("ALTER TABLE " + tableName + " DROP COLUMN " + columnName);
        }
    }

    private void ensureIndexIfMissing(String tableName, String indexName, String sql) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(1)
                FROM information_schema.statistics
                WHERE table_schema = DATABASE()
                  AND table_name = ?
                  AND index_name = ?
                """, Integer.class, tableName, indexName);
        if (count == null || count == 0) {
            tryExecute(sql);
        }
    }

    private boolean columnExists(String tableName, String columnName) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(1)
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = ?
                  AND column_name = ?
                """, Integer.class, tableName, columnName);
        return count != null && count > 0;
    }

    private void tryExecute(String sql) {
        try {
            jdbcTemplate.execute(sql);
        } catch (Exception ex) {
            log.debug("Skip SQL execution [{}]: {}", sql, ex.getMessage());
        }
    }
}

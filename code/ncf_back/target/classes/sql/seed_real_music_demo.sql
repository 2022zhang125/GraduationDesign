-- NCF demo seed SQL (MySQL 8.0+)
-- Purpose:
-- 1) add support tables for preview/favorite
-- 2) insert real song metadata examples
-- 3) provide sample recommendation and favorite data

USE ncf;

-- ----------------------------
-- 1) Ensure support tables
-- ----------------------------
CREATE TABLE IF NOT EXISTS item_media (
    item_id BIGINT NOT NULL COMMENT 'Item ID',
    preview_url VARCHAR(512) NOT NULL COMMENT 'Preview audio URL',
    cover_url VARCHAR(512) NULL COMMENT 'Cover image URL',
    preview_duration_seconds INT DEFAULT 30 COMMENT 'Preview duration in seconds',
    lyric_snippet VARCHAR(1000) NULL COMMENT 'Lyric snippet',
    source_platform VARCHAR(64) NULL COMMENT 'Media source platform',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Update time',
    PRIMARY KEY (item_id)
) COMMENT='Item media extension table (preview url, cover, lyric snippet)';

CREATE TABLE IF NOT EXISTS user_favorite_items (
    user_id BIGINT NOT NULL COMMENT 'User ID',
    item_id BIGINT NOT NULL COMMENT 'Item ID',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Favorite create time',
    PRIMARY KEY (user_id, item_id),
    KEY idx_favorite_item (item_id),
    KEY idx_favorite_created_at (created_at)
) COMMENT='User favorite item relation table';

-- ----------------------------
-- 2) Improve comments on core tables
-- ----------------------------
ALTER TABLE users COMMENT='User master table (account and profile basics)';
ALTER TABLE items COMMENT='Music item master table (song/artist/album basics)';
ALTER TABLE interactions COMMENT='User-item interaction fact table (play/click/favorite)';
ALTER TABLE training_samples COMMENT='NCF model training samples';
ALTER TABLE rec_results COMMENT='Recommendation result snapshots by request time';
ALTER TABLE item_features_daily COMMENT='Daily aggregated item features';
ALTER TABLE item_media COMMENT='Item media extension table (preview url, cover, lyric snippet)';
ALTER TABLE user_favorite_items COMMENT='User favorite item relation table';

-- ----------------------------
-- 3) Optional index optimization (idempotent)
-- ----------------------------
SET @idx1_exists = (
    SELECT COUNT(1)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'rec_results'
      AND index_name = 'idx_rec_user_request_rank'
);
SET @idx1_sql = IF(
    @idx1_exists = 0,
    'CREATE INDEX idx_rec_user_request_rank ON rec_results (user_id, request_time, rank_no)',
    'SELECT 1'
);
PREPARE idx_stmt_1 FROM @idx1_sql;
EXECUTE idx_stmt_1;
DEALLOCATE PREPARE idx_stmt_1;

SET @idx2_exists = (
    SELECT COUNT(1)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'item_features_daily'
      AND index_name = 'idx_item_feature_date_item'
);
SET @idx2_sql = IF(
    @idx2_exists = 0,
    'CREATE INDEX idx_item_feature_date_item ON item_features_daily (feature_date, item_id)',
    'SELECT 1'
);
PREPARE idx_stmt_2 FROM @idx2_sql;
EXECUTE idx_stmt_2;
DEALLOCATE PREPARE idx_stmt_2;

-- ----------------------------
-- 4) Cleanup legacy demo items
-- ----------------------------
DROP TEMPORARY TABLE IF EXISTS tmp_legacy_demo_item_ids;
CREATE TEMPORARY TABLE tmp_legacy_demo_item_ids AS
SELECT item_id
FROM items
WHERE title LIKE '示例歌曲%';

DELETE FROM rec_results
WHERE item_id IN (SELECT item_id FROM tmp_legacy_demo_item_ids);

DELETE FROM user_favorite_items
WHERE item_id IN (SELECT item_id FROM tmp_legacy_demo_item_ids);

DELETE FROM item_media
WHERE item_id IN (SELECT item_id FROM tmp_legacy_demo_item_ids);

DELETE FROM interactions
WHERE item_id IN (SELECT item_id FROM tmp_legacy_demo_item_ids);

DELETE FROM training_samples
WHERE item_id IN (SELECT item_id FROM tmp_legacy_demo_item_ids);

DELETE FROM explicit_ratings
WHERE item_id IN (SELECT item_id FROM tmp_legacy_demo_item_ids);

DELETE FROM item_tags
WHERE item_id IN (SELECT item_id FROM tmp_legacy_demo_item_ids);

DELETE FROM item_embeddings
WHERE item_id IN (SELECT item_id FROM tmp_legacy_demo_item_ids);

DELETE FROM item_features_daily
WHERE item_id IN (SELECT item_id FROM tmp_legacy_demo_item_ids);

DELETE FROM user_item_features_daily
WHERE item_id IN (SELECT item_id FROM tmp_legacy_demo_item_ids);

DELETE FROM items
WHERE item_id IN (SELECT item_id FROM tmp_legacy_demo_item_ids);

DROP TEMPORARY TABLE IF EXISTS tmp_legacy_demo_item_ids;

-- ----------------------------
-- 5) Seed users
-- ----------------------------
INSERT INTO users (
    user_id, external_user_no, password_hash, gender, birth_year, register_time, user_status, created_at, updated_at
) VALUES
    (10001, 'music_fan_amy', NULL, 2, 1999, NOW(), 1, NOW(), NOW()),
    (10002, 'music_fan_bob', NULL, 1, 1995, NOW(), 1, NOW(), NOW()),
    (10003, 'music_fan_chen', NULL, 1, 2001, NOW(), 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE
    external_user_no = VALUES(external_user_no),
    gender = VALUES(gender),
    birth_year = VALUES(birth_year),
    updated_at = NOW();

-- ----------------------------
-- 6) Seed real music items
-- ----------------------------
INSERT INTO items (
    item_id, external_item_no, title, artist_id, artist_name, album_id, album_name,
    genre_code, language_code, duration_seconds, release_date, item_status, created_at, updated_at
) VALUES
    (20001, 'JKAPI_WY_20001', 'Yellow', 3001, 'Coldplay', 4001, 'Yellow', 'ROCK', 'EN', 269, '2000-06-26', 1, NOW(), NOW()),
    (20002, 'JKAPI_WY_20002', 'Faded', 3002, 'Alan Walker', 4002, 'Faded', 'EDM', 'EN', 212, '2015-12-03', 1, NOW(), NOW()),
    (20003, 'JKAPI_WY_20003', 'Demons', 3003, 'Imagine Dragons', 4003, 'Night Visions (Deluxe)', 'ROCK', 'EN', 177, '2013-01-28', 1, NOW(), NOW()),
    (20004, 'JKAPI_WY_20004', 'Perfect', 3004, 'Ed Sheeran', 4004, '÷ (Deluxe)', 'POP', 'EN', 263, '2017-03-03', 1, NOW(), NOW()),
    (20005, 'JKAPI_WY_20005', 'Photograph', 3004, 'Ed Sheeran', 4005, 'x (Deluxe Edition)', 'POP', 'EN', 258, '2014-06-20', 1, NOW(), NOW()),
    (20006, 'JKAPI_WY_20006', '光年之外', 3101, 'G.E.M.邓紫棋', 4101, '光年之外', 'POP', 'ZH', 235, '2016-12-30', 1, NOW(), NOW()),
    (20007, 'JKAPI_WY_20007', '演员', 3102, '薛之谦', 4102, '绅士', 'POP', 'ZH', 261, '2015-06-05', 1, NOW(), NOW()),
    (20008, 'JKAPI_WY_20008', '十年', 3103, '陈奕迅', 4103, '黑白灰', 'MANDOPOP', 'ZH', 205, '2003-04-15', 1, NOW(), NOW()),
    (20009, 'JKAPI_WY_20009', '平凡之路', 3104, '朴树', 4104, '猎户星座', 'FOLK', 'ZH', 302, '2014-07-16', 1, NOW(), NOW()),
    (20010, 'JKAPI_WY_20010', '倔强', 3105, '五月天', 4105, '神的孩子都在跳舞', 'ROCK', 'ZH', 290, '2004-11-05', 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE
    title = VALUES(title),
    artist_name = VALUES(artist_name),
    album_name = VALUES(album_name),
    genre_code = VALUES(genre_code),
    language_code = VALUES(language_code),
    duration_seconds = VALUES(duration_seconds),
    release_date = VALUES(release_date),
    item_status = VALUES(item_status),
    updated_at = NOW();

-- ----------------------------
-- 7) Preview media is synced dynamically by backend via jkapi
-- No static demo audio rows are seeded here because jkapi music_url is time-bound.

-- ----------------------------
-- 8) Seed recommendation snapshots (latest by user)
-- ----------------------------
INSERT INTO rec_results (
    request_id, user_id, item_id, model_version, score, rank_no, reason_text, scene, request_time
) VALUES
    ('demo_req_10001_20260305', 10001, 20006, 'ncf_v1_20260305', 0.9821, 1, 'Prefer mandopop ballad style', 'home', NOW()),
    ('demo_req_10001_20260305', 10001, 20007, 'ncf_v1_20260305', 0.9510, 2, 'Matched recent listening history', 'home', NOW()),
    ('demo_req_10001_20260305', 10001, 20009, 'ncf_v1_20260305', 0.9442, 3, 'Similar-user preference hit', 'home', NOW()),
    ('demo_req_10001_20260305', 10001, 20008, 'ncf_v1_20260305', 0.9324, 4, 'Matched language and tempo preference', 'home', NOW()),
    ('demo_req_10002_20260305', 10002, 20001, 'ncf_v1_20260305', 0.9762, 1, 'Matched western pop preference', 'home', NOW()),
    ('demo_req_10002_20260305', 10002, 20002, 'ncf_v1_20260305', 0.9618, 2, 'Correlated with night listening behavior', 'home', NOW()),
    ('demo_req_10002_20260305', 10002, 20005, 'ncf_v1_20260305', 0.9384, 3, 'Close to favorite song style', 'home', NOW()),
    ('demo_req_10002_20260305', 10002, 20003, 'ncf_v1_20260305', 0.9216, 4, 'Classic rock complementary recommendation', 'home', NOW());

-- ----------------------------
-- 9) Seed favorite examples
-- ----------------------------
INSERT INTO user_favorite_items (user_id, item_id, created_at) VALUES
    (10001, 20006, NOW()),
    (10001, 20007, NOW()),
    (10002, 20001, NOW()),
    (10002, 20002, NOW())
ON DUPLICATE KEY UPDATE created_at = VALUES(created_at);

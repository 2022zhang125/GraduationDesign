-- Patch: table comments + legacy demo media cleanup
-- Target DB: ncf

USE ncf;

-- 1) Ensure support tables exist with table comments
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

-- 2) Improve comments on other core tables
ALTER TABLE users COMMENT='User master table (account and profile basics)';
ALTER TABLE items COMMENT='Music item master table (song/artist/album basics)';
ALTER TABLE interactions COMMENT='User-item interaction fact table (play/click/favorite)';
ALTER TABLE training_samples COMMENT='NCF model training samples';
ALTER TABLE rec_results COMMENT='Recommendation result snapshots by request time';
ALTER TABLE item_features_daily COMMENT='Daily aggregated item features';
ALTER TABLE item_media COMMENT='Item media extension table (preview url, cover, lyric snippet)';
ALTER TABLE user_favorite_items COMMENT='User favorite item relation table';

-- 3) Remove legacy demo preview rows and legacy sample items.
-- The backend will sync real music_url values from jkapi on demand.
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
WHERE item_id IN (SELECT item_id FROM tmp_legacy_demo_item_ids)
   OR source_platform = 'DEMO_CLIP'
   OR preview_url LIKE 'https://www.soundhelix.com/examples/mp3/%';

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

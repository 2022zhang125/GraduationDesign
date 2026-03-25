package com.example.ncfback.service;

import com.example.ncfback.config.MusicCatalogProperties;
import com.example.ncfback.dto.YaohuMusicSearchResponse;
import com.example.ncfback.entity.ItemMedia;
import com.example.ncfback.mapper.ItemMediaMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.CRC32;

@Slf4j
@Service
@RequiredArgsConstructor
public class MusicCatalogSyncService {

    private final JdbcTemplate jdbcTemplate;
    private final ItemMediaMapper itemMediaMapper;
    private final YaohuMusicApiService yaohuMusicApiService;
    private final MusicCatalogProperties properties;
    private final ResourceLoader resourceLoader;

    public boolean needsSync() {
        Long totalItems = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM items", Long.class);
        if (totalItems == null || totalItems == 0) {
            return false;
        }

        Long unsyncedItems = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM items
                WHERE title LIKE '示例歌曲%'
                   OR external_item_no IS NULL
                   OR external_item_no NOT LIKE 'YAOHU_%_QUERY::%'
                """, Long.class);
        Long syncedMedia = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM item_media
                WHERE source_platform LIKE 'YAOHU_%'
                """, Long.class);
        return (unsyncedItems != null && unsyncedItems > 0)
                || syncedMedia == null
                || syncedMedia < totalItems;
    }

    public SyncSummary syncAll() {
        List<Long> itemIds = jdbcTemplate.queryForList(
                "SELECT item_id FROM items ORDER BY item_id",
                Long.class
        );
        if (itemIds.isEmpty()) {
            return new SyncSummary(0, 0, 0);
        }

        List<CatalogEntry> catalog = loadCatalog();
        int assigned = 0;
        int attempted = 0;
        Set<String> usedTracks = new HashSet<>();

        for (CatalogEntry entry : catalog) {
            if (assigned >= itemIds.size()) {
                break;
            }
            attempted++;
            var responseOpt = yaohuMusicApiService.search("wyvip", entry.query(), 5);
            if (responseOpt.isEmpty() || responseOpt.get().getSongs().isEmpty()) {
                continue;
            }

            YaohuMusicSearchResponse.SongOption song = responseOpt.get().getSongs().get(0);
            String uniqueKey = song.getName() + "|" + song.getSinger();
            if (!usedTracks.add(uniqueKey)) {
                continue;
            }

            Long itemId = itemIds.get(assigned);
            syncItem(itemId, entry, song);
            assigned++;

            if (assigned % 10 == 0 || assigned == itemIds.size()) {
                log.info("Synced real music catalog progress: {}/{}", assigned, itemIds.size());
            }
        }

        if (assigned < itemIds.size()) {
            log.warn("Real music catalog sync incomplete: synced {} of {} items", assigned, itemIds.size());
        }

        return new SyncSummary(itemIds.size(), assigned, attempted);
    }

    private void syncItem(Long itemId, CatalogEntry entry, YaohuMusicSearchResponse.SongOption song) {
        jdbcTemplate.update("""
                UPDATE items
                SET external_item_no = ?,
                    title = ?,
                    artist_id = ?,
                    artist_name = ?,
                    album_id = ?,
                    album_name = ?,
                    genre_code = ?,
                    language_code = ?,
                    duration_seconds = NULL,
                    release_date = NULL,
                    item_status = 1,
                    updated_at = NOW()
                WHERE item_id = ?
                """,
                YaohuMusicApiService.buildCatalogKey("wyvip", entry.query(), song.getIndex()),
                song.getName(),
                stableId("artist", song.getSinger(), 1_000_000L),
                song.getSinger(),
                stableId("album", song.getSinger() + "|" + song.getAlbum(), 2_000_000L),
                song.getAlbum(),
                entry.genreCode(),
                entry.languageCode(),
                itemId
        );

        ItemMedia existingMedia = itemMediaMapper.findByItemId(itemId);
        yaohuMusicApiService.fetchTrackDetail("wyvip", entry.query(), song.getIndex(), null)
                .map(detail -> yaohuMusicApiService.buildMedia(itemId, detail, existingMedia))
                .ifPresent(itemMediaMapper::upsert);
    }

    private List<CatalogEntry> loadCatalog() {
        try {
            Resource resource = resourceLoader.getResource(properties.getResourcePath());
            List<CatalogEntry> entries = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String trimmed = line.trim();
                    if (!StringUtils.hasText(trimmed) || trimmed.startsWith("#")) {
                        continue;
                    }
                    String[] parts = trimmed.split("\\|");
                    if (parts.length < 3) {
                        continue;
                    }
                    entries.add(new CatalogEntry(parts[0].trim(), parts[1].trim(), parts[2].trim()));
                }
            }
            return entries;
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to load real music catalog resource", ex);
        }
    }

    private long stableId(String prefix, String value, long base) {
        CRC32 crc32 = new CRC32();
        crc32.update((prefix + ":" + value).getBytes(StandardCharsets.UTF_8));
        return base + crc32.getValue();
    }

    public record SyncSummary(int totalItems, int syncedItems, int attemptedQueries) {
    }

    private record CatalogEntry(String query, String genreCode, String languageCode) {
    }
}

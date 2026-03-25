package com.example.ncfback.service;

import com.example.ncfback.dto.MusicPlaybackDetailResponse;
import com.example.ncfback.dto.MusicSearchResult;
import com.example.ncfback.dto.YaohuMusicSearchResponse;
import com.example.ncfback.entity.Item;
import com.example.ncfback.entity.ItemMedia;
import com.example.ncfback.mapper.ItemMediaMapper;
import com.example.ncfback.mapper.ItemMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.CRC32;

@Service
@RequiredArgsConstructor
public class MusicSearchService {

    private final YaohuMusicApiService yaohuMusicApiService;
    private final ItemMapper itemMapper;
    private final ItemMediaMapper itemMediaMapper;

    @Transactional
    public List<MusicSearchResult> search(String query, Integer limit, String level, String source) {
        if (!StringUtils.hasText(query)) {
            throw new IllegalArgumentException("query cannot be blank");
        }

        List<String> sources = resolveSources(source);
        List<MusicSearchResult> results = new ArrayList<>();
        for (String resolvedSource : sources) {
            results.addAll(searchBySource(query, limit, level, resolvedSource));
        }
        if (results.isEmpty()) {
            throw new IllegalArgumentException("No music result found for query: " + query);
        }
        return results;
    }

    public String getPlaybackUrl(String query,
                                 Integer index,
                                 String level,
                                 String source,
                                 Long itemId,
                                 String title,
                                 String album,
                                 String artist) {
        return resolveTrackDetail(query, index, level, source, itemId, title, album, artist).musicUrl();
    }

    public MusicPlaybackDetailResponse getPlaybackDetail(String query,
                                                         Integer index,
                                                         String level,
                                                         String source,
                                                         Long itemId,
                                                         String title,
                                                         String album,
                                                         String artist) {
        String normalizedLevel = yaohuMusicApiService.normalizeLevel(level);
        String resolvedSource = yaohuMusicApiService.normalizeSource(source);
        YaohuMusicApiService.TrackDetail detail = resolveTrackDetail(
                query, index, normalizedLevel, resolvedSource, itemId, title, album, artist
        );
        Item resolvedItem = persistResolvedMedia(itemId, resolvedSource, query, detail, title, artist);

        MusicPlaybackDetailResponse response = new MusicPlaybackDetailResponse();
        response.setSource(resolvedSource);
        response.setQuery(query);
        response.setTrackIndex(index);
        response.setQualityLevel(detail.qualityLevel());
        response.setPlayUrl(buildResolvedPlayUrl(
                resolvedItem, query, index, normalizedLevel, resolvedSource, itemId, title, album, artist
        ));
        response.setTitle(detail.name());
        response.setArtistName(detail.singer());
        response.setAlbumName(detail.album());
        response.setMusicUrl(detail.musicUrl());
        response.setCoverUrl(detail.coverUrl());
        response.setLyricsText(detail.lyricText());
        response.setSourcePlatform("YAOHU_" + resolvedSource.toUpperCase(Locale.ROOT) + "_DETAIL");
        return response;
    }

    public YaohuMusicApiService.MediaStreamResponse getPlaybackStream(String query,
                                                                      Integer index,
                                                                      String level,
                                                                      String source,
                                                                      Long itemId,
                                                                      String title,
                                                                      String album,
                                                                      String artist,
                                                                      String rangeHeader) {
        YaohuMusicApiService.TrackDetail detail = resolveTrackDetail(
                query, index, level, source, itemId, title, album, artist
        );
        persistResolvedMedia(itemId, yaohuMusicApiService.normalizeSource(source), query, detail, title, artist);
        return yaohuMusicApiService.downloadMedia(detail.musicUrl(), rangeHeader);
    }

    private List<MusicSearchResult> searchBySource(String query, Integer limit, String level, String source) {
        String normalizedLevel = yaohuMusicApiService.normalizeLevel(level);
        YaohuMusicSearchResponse response = yaohuMusicApiService.search(source, query, limit).orElse(null);
        if (response == null || response.getSongs().isEmpty()) {
            return List.of();
        }

        List<MusicSearchResult> results = new ArrayList<>();
        for (YaohuMusicSearchResponse.SongOption song : response.getSongs()) {
            if (song.getIndex() == null || song.getIndex() <= 0 || !StringUtils.hasText(song.getName())) {
                continue;
            }

            String externalItemNo = YaohuMusicApiService.buildCatalogKey(source, query, song.getIndex());
            Item existingItem = itemMapper.findByExternalItemNo(externalItemNo);
            Item resolvedItem = existingItem != null ? existingItem : createSearchItem(source, query, song);

            MusicSearchResult result = new MusicSearchResult();
            result.setQuery(query);
            result.setSource(source);
            result.setSourceLabel(toSourceLabel(source));
            result.setTrackIndex(song.getIndex());
            result.setQualityLevel(normalizedLevel);
            result.setItemId(resolvedItem.getItemId());
            result.setTitle(song.getName());
            result.setAlbumName(song.getAlbum());
            result.setArtistName(song.getSinger());
            result.setLyricSnippet(yaohuMusicApiService.sanitizeLyricSnippet(
                    song.getName() + " - " + song.getSinger() + (StringUtils.hasText(song.getAlbum()) ? " / " + song.getAlbum() : "")
            ));
            result.setPayTag(song.getPayTag());
            result.setMusicUrl("");
            result.setCoverUrl(song.getCoverUrl());
            result.setPlayUrl(buildPlayUrl(
                    query, song.getIndex(), normalizedLevel, source,
                    resolvedItem.getItemId(), song.getName(), song.getAlbum(), song.getSinger()
            ));
            result.setSourcePlatform("YAOHU_" + source.toUpperCase(Locale.ROOT) + "_SEARCH");
            result.setInCatalog(existingItem != null);
            results.add(result);
        }
        return results;
    }

    private List<String> resolveSources(String source) {
        if (!StringUtils.hasText(source) || "all".equalsIgnoreCase(source.trim())) {
            return List.of("wyvip", "qq_plus");
        }
        return List.of(yaohuMusicApiService.normalizeSource(source));
    }

    private String buildPlayUrl(String query,
                                Integer index,
                                String level,
                                String source,
                                Long itemId,
                                String title,
                                String album,
                                String artist) {
        UriComponentsBuilder builder = UriComponentsBuilder.newInstance()
                .path("/api/music/play")
                .queryParam("query", query)
                .queryParam("n", index)
                .queryParam("source", source)
                .queryParam("level", level);
        if (itemId != null && itemId > 0) {
            builder.queryParam("itemId", itemId);
        }
        if (StringUtils.hasText(title)) {
            builder.queryParam("title", title);
        }
        if (StringUtils.hasText(album)) {
            builder.queryParam("album", album);
        }
        if (StringUtils.hasText(artist)) {
            builder.queryParam("artist", artist);
        }
        return builder.build().encode().toUriString();
    }

    private String buildResolvedPlayUrl(Item item,
                                        String query,
                                        Integer index,
                                        String level,
                                        String source,
                                        Long itemId,
                                        String title,
                                        String album,
                                        String artist) {
        if (item != null && item.getItemId() != null) {
            return "/api/media/items/" + item.getItemId() + "/play";
        }
        return buildPlayUrl(query, index, level, source, itemId, title, album, artist);
    }

    private YaohuMusicApiService.TrackDetail resolveTrackDetail(String query,
                                                                Integer index,
                                                                String level,
                                                                String source,
                                                                Long itemId,
                                                                String title,
                                                                String album,
                                                                String artist) {
        if (!StringUtils.hasText(query)) {
            throw new IllegalArgumentException("query cannot be blank");
        }
        if (index == null || index <= 0) {
            throw new IllegalArgumentException("n must be greater than 0");
        }

        String resolvedSource = yaohuMusicApiService.normalizeSource(source);
        var directDetail = yaohuMusicApiService.fetchTrackDetail(resolvedSource, query, index, level)
                .filter(track -> StringUtils.hasText(track.musicUrl()));
        if (directDetail.isPresent()) {
            return directDetail.get();
        }

        Item item = resolveItem(itemId, resolvedSource, query, index, title, artist);
        return yaohuMusicApiService.fetchTrackDetailByMetadata(item, title, album, artist, level, resolvedSource)
                .filter(track -> StringUtils.hasText(track.musicUrl()))
                .orElseThrow(() -> new IllegalArgumentException(
                        "No playable music result found for query: " + query + ", n=" + index
                ));
    }

    private Item persistResolvedMedia(Long itemId,
                                      String source,
                                      String query,
                                      YaohuMusicApiService.TrackDetail detail,
                                      String title,
                                      String artist) {
        if (detail == null) {
            return null;
        }

        Item item = resolveItem(itemId, source, query, detail.index(), title, artist);
        if (item == null) {
            return null;
        }

        ItemMedia existingMedia = itemMediaMapper.findByItemId(item.getItemId());
        ItemMedia media = yaohuMusicApiService.buildMedia(item.getItemId(), detail, existingMedia);
        itemMediaMapper.upsert(media);
        return item;
    }

    private Item resolveItem(Long itemId,
                             String source,
                             String query,
                             Integer index,
                             String title,
                             String artist) {
        if (itemId != null && itemId > 0) {
            Item byId = itemMapper.findById(itemId);
            if (byId != null) {
                return byId;
            }
        }

        if (StringUtils.hasText(query) && index != null && index > 0) {
            String externalItemNo = YaohuMusicApiService.buildCatalogKey(source, query, index);
            Item byExternalNo = itemMapper.findByExternalItemNo(externalItemNo);
            if (byExternalNo != null) {
                return byExternalNo;
            }
        }

        if (StringUtils.hasText(title) && StringUtils.hasText(artist)) {
            return itemMapper.findByTitleAndArtist(title, artist);
        }
        return null;
    }

    private synchronized Item createSearchItem(String source, String query, YaohuMusicSearchResponse.SongOption song) {
        String externalItemNo = YaohuMusicApiService.buildCatalogKey(source, query, song.getIndex());
        Item existing = itemMapper.findByExternalItemNo(externalItemNo);
        if (existing != null) {
            return existing;
        }

        LocalDateTime now = LocalDateTime.now();
        Item item = new Item();
        item.setItemId(itemMapper.findNextItemId());
        item.setExternalItemNo(externalItemNo);
        item.setTitle(song.getName());
        item.setArtistId(stableId("artist", song.getSinger(), 1_000_000L));
        item.setArtistName(song.getSinger());
        item.setAlbumId(stableId("album", song.getSinger() + "|" + song.getAlbum(), 2_000_000L));
        item.setAlbumName(song.getAlbum());
        item.setGenreCode("POP");
        item.setLanguageCode(detectLanguage(song.getName(), song.getSinger(), song.getAlbum()));
        item.setDurationSeconds(30);
        item.setItemStatus(1);
        item.setCreatedAt(now);
        item.setUpdatedAt(now);
        itemMapper.insert(item);
        return item;
    }

    private String toSourceLabel(String source) {
        return "qq_plus".equals(source) ? "QQ音乐" : "网易云";
    }

    private long stableId(String prefix, String value, long base) {
        CRC32 crc32 = new CRC32();
        crc32.update((prefix + ":" + value).getBytes(StandardCharsets.UTF_8));
        return base + crc32.getValue();
    }

    private String detectLanguage(String... values) {
        for (String value : values) {
            if (!StringUtils.hasText(value)) {
                continue;
            }
            for (int index = 0; index < value.length(); index += 1) {
                Character.UnicodeScript script = Character.UnicodeScript.of(value.charAt(index));
                if (script == Character.UnicodeScript.HAN) {
                    return "ZH";
                }
            }
        }
        return "EN";
    }
}

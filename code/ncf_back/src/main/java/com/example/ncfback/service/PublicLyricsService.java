package com.example.ncfback.service;

import com.example.ncfback.dto.LyricsLookupResponse;
import com.example.ncfback.entity.Item;
import com.example.ncfback.entity.ItemMedia;
import com.example.ncfback.mapper.ItemMediaMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class PublicLyricsService {

    private static final String SOURCE_YAOHU_API = "YAOHU_API";
    private static final String SOURCE_MEDIA_FALLBACK = "ITEM_MEDIA_FALLBACK";
    private static final Duration LOOKUP_CACHE_TTL = Duration.ofHours(2);
    private static final Duration MISS_CACHE_TTL = Duration.ofMinutes(15);

    private final ItemService itemService;
    private final ItemMediaMapper itemMediaMapper;
    private final YaohuMusicApiService yaohuMusicApiService;
    private final Map<String, CacheEntry<Optional<LyricsLookupResponse>>> lookupCache = new ConcurrentHashMap<>();

    public LyricsLookupResponse lookup(Long itemId, String title, String album, String artist) {
        LyricsContext context = resolveContext(itemId, title, album, artist);
        String cacheKey = buildAssetKey(context.itemId(), context.title(), context.album(), context.artist());
        Optional<LyricsLookupResponse> cached = getCachedValue(cacheKey);
        if (cached != null) {
            return cached.orElseGet(() -> buildFallbackResponse(context));
        }

        try {
            Optional<YaohuMusicApiService.TrackDetail> detailOpt =
                    yaohuMusicApiService.fetchTrackDetailByMetadata(
                            context.item(), context.title(), context.album(), context.artist(), null
                    );
            if (detailOpt.isPresent()) {
                LyricsLookupResponse response = toLyricsLookupResponse(context, detailOpt.get(), SOURCE_YAOHU_API);
                putCachedValue(cacheKey, Optional.of(response), LOOKUP_CACHE_TTL);
                return response;
            }
        } catch (Exception ex) {
            log.warn("Failed to fetch yaohu lyrics asset for itemId={}, title={}, artist={}: {}",
                    context.itemId(), context.title(), context.artist(), ex.getMessage());
        }

        LyricsLookupResponse fallback = buildFallbackResponse(context);
        putCachedValue(cacheKey, Optional.of(fallback), StringUtils.hasText(fallback.getLyricsText()) || StringUtils.hasText(fallback.getCoverUrl())
                ? LOOKUP_CACHE_TTL
                : MISS_CACHE_TTL);
        return fallback;
    }

    public String lookupLyricSnippet(Long itemId, String title, String album, String artist) {
        LyricsLookupResponse response = lookup(itemId, title, album, artist);
        if (response != null && StringUtils.hasText(response.getLyricsText())) {
            return yaohuMusicApiService.sanitizeLyricSnippet(response.getLyricsText());
        }
        return yaohuMusicApiService.sanitizeLyricSnippet(
                firstNonBlank(title, response != null ? response.getTitle() : null, "") + " - "
                        + firstNonBlank(artist, response != null ? response.getArtistName() : null, "")
                        + (StringUtils.hasText(firstNonBlank(album, response != null ? response.getAlbumName() : null))
                        ? " / " + firstNonBlank(album, response != null ? response.getAlbumName() : null)
                        : "")
        );
    }

    public void warmUpAssetsAsync(Long itemId, String title, String album, String artist) {
        LyricsContext context = resolveContext(itemId, title, album, artist);
        if (!StringUtils.hasText(context.title()) && !StringUtils.hasText(context.artist())) {
            return;
        }
        CompletableFuture.runAsync(() -> {
            try {
                lookup(context.itemId(), context.title(), context.album(), context.artist());
            } catch (Exception ex) {
                log.debug("Warm up assets ignored for itemId={}: {}", context.itemId(), ex.getMessage());
            }
        });
    }

    public CoverAsset loadCover(Long itemId, String title, String album, String artist) {
        LyricsLookupResponse response = lookup(itemId, title, album, artist);
        if (!StringUtils.hasText(response.getCoverUrl())) {
            return null;
        }
        return new CoverAsset(null, null, response.getCoverUrl());
    }

    private LyricsContext resolveContext(Long itemId, String title, String album, String artist) {
        Item item = null;
        if (itemId != null && itemId > 0) {
            item = itemService.getById(itemId);
        } else {
            itemId = null;
        }

        String resolvedTitle = firstNonBlank(title, item != null ? item.getTitle() : null);
        String resolvedAlbum = normalizeAlbum(firstNonBlank(album, item != null ? item.getAlbumName() : null));
        String resolvedArtist = firstNonBlank(artist, item != null ? item.getArtistName() : null);
        ItemMedia media = itemId != null ? itemMediaMapper.findByItemId(itemId) : null;
        return new LyricsContext(itemId, item, resolvedTitle, resolvedAlbum, resolvedArtist, media);
    }

    private LyricsLookupResponse toLyricsLookupResponse(LyricsContext context,
                                                        YaohuMusicApiService.TrackDetail detail,
                                                        String source) {
        LyricsLookupResponse response = new LyricsLookupResponse();
        response.setItemId(context.itemId());
        response.setTitle(firstNonBlank(detail.name(), context.title()));
        response.setAlbumName(firstNonBlank(detail.album(), context.album()));
        response.setArtistName(firstNonBlank(detail.singer(), context.artist()));
        response.setLyricsText(StringUtils.hasText(detail.lyricText())
                ? detail.lyricText().replace("\r\n", "\n").trim()
                : null);
        response.setCoverUrl(firstNonBlank(detail.coverUrl(), context.media() != null ? context.media().getCoverUrl() : null));
        response.setSource(source);
        return response;
    }

    private LyricsLookupResponse buildFallbackResponse(LyricsContext context) {
        LyricsLookupResponse response = new LyricsLookupResponse();
        response.setItemId(context.itemId());
        response.setTitle(context.title());
        response.setAlbumName(context.album());
        response.setArtistName(context.artist());
        response.setLyricsText(context.media() != null ? context.media().getLyricSnippet() : null);
        response.setCoverUrl(context.media() != null ? context.media().getCoverUrl() : null);
        response.setSource(SOURCE_MEDIA_FALLBACK);
        return response;
    }

    private Optional<LyricsLookupResponse> getCachedValue(String key) {
        CacheEntry<Optional<LyricsLookupResponse>> entry = lookupCache.get(key);
        if (entry == null) {
            return null;
        }
        if (entry.expiresAtMillis() < System.currentTimeMillis()) {
            lookupCache.remove(key);
            return null;
        }
        return entry.value();
    }

    private void putCachedValue(String key, Optional<LyricsLookupResponse> value, Duration ttl) {
        lookupCache.put(key, new CacheEntry<>(value, System.currentTimeMillis() + ttl.toMillis()));
    }

    private String buildAssetKey(Long itemId, String title, String album, String artist) {
        return String.join("::",
                itemId != null ? String.valueOf(itemId) : "",
                normalizeKeyPart(title),
                normalizeKeyPart(album),
                normalizeKeyPart(artist));
    }

    private String normalizeKeyPart(String value) {
        return StringUtils.hasText(value) ? value.trim().toLowerCase() : "";
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private String normalizeAlbum(String album) {
        if (!StringUtils.hasText(album)) {
            return null;
        }
        return "[Unknown Album]".equalsIgnoreCase(album.trim()) ? null : album;
    }

    public record CoverAsset(byte[] bytes, String contentType, String redirectUrl) {
    }

    private record LyricsContext(Long itemId, Item item, String title, String album, String artist, ItemMedia media) {
    }

    private record CacheEntry<T>(T value, long expiresAtMillis) {
    }
}

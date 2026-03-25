package com.example.ncfback.service;

import com.example.ncfback.entity.ItemMedia;
import com.example.ncfback.mapper.ItemMediaMapper;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class MediaService {

    private final ItemMediaMapper itemMediaMapper;
    private final ItemService itemService;
    private final YaohuMusicApiService yaohuMusicApiService;

    public ItemMedia getPreviewByItemId(Long itemId) {
        return resolvePreview(itemId)
                .map(this::toClientMedia)
                .orElseThrow(() -> new IllegalArgumentException("Preview not found for itemId: " + itemId));
    }

    public List<ItemMedia> getPreviewByItemIds(List<Long> itemIds) {
        if (itemIds == null || itemIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<ItemMedia> resolved = new ArrayList<>();
        for (Long itemId : new LinkedHashSet<>(itemIds)) {
            if (itemId == null) {
                continue;
            }
            tryResolvePreview(itemId)
                    .map(this::toClientMedia)
                    .ifPresent(resolved::add);
        }
        return resolved;
    }

    public String getPlaybackUrl(Long itemId) {
        return resolvePlaybackMedia(itemId, false, null).getPreviewUrl();
    }

    public YaohuMusicApiService.MediaStreamResponse getPlaybackStream(Long itemId, String rangeHeader) {
        ItemMedia media = resolvePlaybackMedia(itemId, false, null);
        try {
            return yaohuMusicApiService.downloadMedia(media.getPreviewUrl(), rangeHeader);
        } catch (Exception ex) {
            log.warn("Primary media download failed for itemId={}: {}", itemId, ex.getMessage());
        }

        String alternateSource = resolveAlternateSource(media, itemService.getById(itemId));
        if (alternateSource != null) {
            try {
                ItemMedia alternateMedia = resolvePlaybackMedia(itemId, true, alternateSource);
                if (!Objects.equals(media.getPreviewUrl(), alternateMedia.getPreviewUrl())) {
                    return yaohuMusicApiService.downloadMedia(alternateMedia.getPreviewUrl(), rangeHeader);
                }
            } catch (Exception ex) {
                log.warn("Alternate source {} media download failed for itemId={}: {}", alternateSource, itemId, ex.getMessage());
            }
        }

        ItemMedia refreshedMedia = resolvePlaybackMedia(itemId, true, null);
        if (!Objects.equals(media.getPreviewUrl(), refreshedMedia.getPreviewUrl())) {
            return yaohuMusicApiService.downloadMedia(refreshedMedia.getPreviewUrl(), rangeHeader);
        }

        throw new IllegalStateException("Failed to download upstream media for itemId: " + itemId);
    }

    private Optional<ItemMedia> tryResolvePreview(Long itemId) {
        try {
            return resolvePreview(itemId);
        } catch (Exception ex) {
            log.warn("Skip preview sync for itemId={}: {}", itemId, ex.getMessage());
            return Optional.empty();
        }
    }

    private Optional<ItemMedia> resolvePreview(Long itemId) {
        var item = itemService.getById(itemId);
        ItemMedia media = itemMediaMapper.findByItemId(itemId);
        if (!yaohuMusicApiService.shouldRefresh(media)) {
            return Optional.of(media);
        }

        Optional<ItemMedia> refreshed = refreshMedia(item, media, null);
        if (refreshed.isPresent()) {
            itemMediaMapper.upsert(refreshed.get());
            return Optional.ofNullable(itemMediaMapper.findByItemId(itemId));
        }
        return Optional.ofNullable(media)
                .filter(existing -> Objects.nonNull(existing.getPreviewUrl()) && !"DEMO_CLIP".equalsIgnoreCase(existing.getSourcePlatform()));
    }

    private ItemMedia resolvePlaybackMedia(Long itemId, boolean forceRefresh, String preferredSource) {
        var item = itemService.getById(itemId);
        ItemMedia media = itemMediaMapper.findByItemId(itemId);
        if (forceRefresh || yaohuMusicApiService.shouldRefresh(media)) {
            Optional<ItemMedia> refreshed = refreshMedia(item, media, preferredSource);
            if (refreshed.isPresent()) {
                itemMediaMapper.upsert(refreshed.get());
                media = itemMediaMapper.findByItemId(itemId);
            }
        }

        if (media == null || media.getPreviewUrl() == null || media.getPreviewUrl().isBlank()) {
            throw new IllegalArgumentException("Preview not found for itemId: " + itemId);
        }
        return media;
    }

    private Optional<ItemMedia> refreshMedia(com.example.ncfback.entity.Item item, ItemMedia existingMedia, String preferredSource) {
        Optional<ItemMedia> refreshed = yaohuMusicApiService.fetchPreview(item, existingMedia, preferredSource);
        if (refreshed.isPresent()) {
            return refreshed;
        }

        return yaohuMusicApiService.fetchTrackDetailByMetadata(item, item.getTitle(), item.getAlbumName(), item.getArtistName(), null, preferredSource)
                .filter(detail -> detail.musicUrl() != null && !detail.musicUrl().isBlank())
                .map(detail -> yaohuMusicApiService.buildMedia(item.getItemId(), detail, existingMedia));
    }

    private String resolveAlternateSource(ItemMedia media, com.example.ncfback.entity.Item item) {
        String source = resolveCurrentSource(media, item);
        if ("wyvip".equals(source)) {
            return "qq_plus";
        }
        if ("qq_plus".equals(source)) {
            return "wyvip";
        }
        return null;
    }

    private String resolveCurrentSource(ItemMedia media, com.example.ncfback.entity.Item item) {
        if (media != null && media.getSourcePlatform() != null) {
            String sourcePlatform = media.getSourcePlatform().toLowerCase();
            if (sourcePlatform.contains("qq_plus")) {
                return "qq_plus";
            }
            if (sourcePlatform.contains("wyvip")) {
                return "wyvip";
            }
        }
        if (item != null && item.getExternalItemNo() != null) {
            String externalItemNo = item.getExternalItemNo().toLowerCase();
            if (externalItemNo.startsWith("yaohu_qq_plus")) {
                return "qq_plus";
            }
            if (externalItemNo.startsWith("yaohu_wyvip")) {
                return "wyvip";
            }
        }
        return null;
    }

    private ItemMedia toClientMedia(ItemMedia media) {
        ItemMedia clientMedia = new ItemMedia();
        clientMedia.setItemId(media.getItemId());
        clientMedia.setMusicUrl(media.getPreviewUrl());
        clientMedia.setPlayUrl(buildPlayUrl(media.getItemId()));
        clientMedia.setPreviewUrl(buildPlayUrl(media.getItemId()));
        clientMedia.setCoverUrl(media.getCoverUrl());
        clientMedia.setPreviewDurationSeconds(media.getPreviewDurationSeconds());
        clientMedia.setLyricSnippet(media.getLyricSnippet());
        clientMedia.setSourcePlatform(media.getSourcePlatform());
        clientMedia.setUpdatedAt(media.getUpdatedAt());
        return clientMedia;
    }

    private String buildPlayUrl(Long itemId) {
        return "/api/media/items/" + itemId + "/play";
    }
}

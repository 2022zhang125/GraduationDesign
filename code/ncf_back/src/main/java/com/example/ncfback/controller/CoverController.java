package com.example.ncfback.controller;

import com.example.ncfback.service.PublicLyricsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.Duration;

@RestController
@RequestMapping("/api/cover")
@RequiredArgsConstructor
public class CoverController {

    private final PublicLyricsService publicLyricsService;

    @GetMapping
    public ResponseEntity<?> cover(@RequestParam(required = false) String itemId,
                                   @RequestParam(required = false) String title,
                                   @RequestParam(required = false) String album,
                                   @RequestParam(required = false) String artist) {
        PublicLyricsService.CoverAsset cover = publicLyricsService.loadCover(parseItemId(itemId), title, album, artist);
        if (cover == null) {
            return ResponseEntity.notFound().build();
        }
        if (StringUtils.hasText(cover.redirectUrl())) {
            return ResponseEntity.status(HttpStatus.FOUND)
                    .location(URI.create(cover.redirectUrl()))
                    .build();
        }

        MediaType mediaType = StringUtils.hasText(cover.contentType())
                ? MediaType.parseMediaType(cover.contentType())
                : MediaType.IMAGE_JPEG;
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofHours(6)).cachePublic())
                .contentType(mediaType)
                .body(cover.bytes());
    }

    private Long parseItemId(String rawItemId) {
        if (rawItemId == null || rawItemId.isBlank()) {
            return null;
        }
        try {
            Long itemId = Long.valueOf(rawItemId.trim());
            return itemId > 0 ? itemId : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}

package com.example.ncfback.controller;

import com.example.ncfback.dto.ApiResponse;
import com.example.ncfback.dto.LyricsLookupResponse;
import com.example.ncfback.service.PublicLyricsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/lyrics")
@RequiredArgsConstructor
public class LyricsController {

    private final PublicLyricsService publicLyricsService;

    @GetMapping("/lookup")
    public ApiResponse<LyricsLookupResponse> lookup(@RequestParam(required = false) String itemId,
                                                    @RequestParam(required = false) String title,
                                                    @RequestParam(required = false) String album,
                                                    @RequestParam(required = false) String artist) {
        return ApiResponse.ok(publicLyricsService.lookup(parseItemId(itemId), title, album, artist));
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

package com.example.ncfback.controller;

import com.example.ncfback.dto.ApiResponse;
import com.example.ncfback.dto.MusicPlaybackDetailResponse;
import com.example.ncfback.dto.MusicSearchResult;
import com.example.ncfback.service.MusicSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/music")
@RequiredArgsConstructor
public class MusicSearchController {

    private final MusicSearchService musicSearchService;

    @GetMapping("/search")
    public ApiResponse<List<MusicSearchResult>> search(@RequestParam String query,
                                                       @RequestParam(required = false) Integer limit,
                                                       @RequestParam(required = false) String level,
                                                       @RequestParam(required = false) String source) {
        return ApiResponse.ok(musicSearchService.search(query, limit, level, source));
    }

    @GetMapping("/detail")
    public ApiResponse<MusicPlaybackDetailResponse> detail(@RequestParam String query,
                                                           @RequestParam Integer n,
                                                           @RequestParam(required = false) String level,
                                                           @RequestParam(required = false) String source,
                                                           @RequestParam(required = false) Long itemId,
                                                           @RequestParam(required = false) String title,
                                                           @RequestParam(required = false) String album,
                                                           @RequestParam(required = false) String artist) {
        return ApiResponse.ok(musicSearchService.getPlaybackDetail(query, n, level, source, itemId, title, album, artist));
    }

    @GetMapping("/play")
    public ResponseEntity<byte[]> play(@RequestParam String query,
                                       @RequestParam Integer n,
                                       @RequestParam(required = false) String level,
                                       @RequestParam(required = false) String source,
                                       @RequestParam(required = false) Long itemId,
                                       @RequestParam(required = false) String title,
                                       @RequestParam(required = false) String album,
                                       @RequestParam(required = false) String artist,
                                       @RequestHeader(value = HttpHeaders.RANGE, required = false) String rangeHeader) {
        var stream = musicSearchService.getPlaybackStream(query, n, level, source, itemId, title, album, artist, rangeHeader);
        HttpHeaders headers = new HttpHeaders();
        copyHeader(stream, headers, "Content-Type");
        copyHeader(stream, headers, "Content-Length");
        copyHeader(stream, headers, "Content-Range");
        copyHeader(stream, headers, "Accept-Ranges");
        copyHeader(stream, headers, "Cache-Control");
        copyHeader(stream, headers, "ETag");
        copyHeader(stream, headers, "Last-Modified");
        return new ResponseEntity<>(stream.body(), headers, HttpStatus.valueOf(stream.statusCode()));
    }

    private void copyHeader(com.example.ncfback.service.YaohuMusicApiService.MediaStreamResponse stream,
                            HttpHeaders headers,
                            String headerName) {
        stream.headers().firstValue(headerName).ifPresent(value -> headers.add(headerName, value));
    }
}

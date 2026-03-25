package com.example.ncfback.controller;

import com.example.ncfback.dto.ApiResponse;
import com.example.ncfback.entity.ItemMedia;
import com.example.ncfback.service.MediaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/media")
@RequiredArgsConstructor
public class MediaController {

    private final MediaService mediaService;

    @GetMapping("/items/{itemId}/preview")
    public ApiResponse<ItemMedia> preview(@PathVariable Long itemId) {
        return ApiResponse.ok(mediaService.getPreviewByItemId(itemId));
    }

    @GetMapping("/items/previews")
    public ApiResponse<List<ItemMedia>> previews(@RequestParam List<Long> itemIds) {
        return ApiResponse.ok(mediaService.getPreviewByItemIds(itemIds));
    }

    @GetMapping("/items/{itemId}/play")
    public ResponseEntity<byte[]> play(@PathVariable Long itemId,
                                       @RequestHeader(value = HttpHeaders.RANGE, required = false) String rangeHeader) {
        var stream = mediaService.getPlaybackStream(itemId, rangeHeader);
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

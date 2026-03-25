package com.example.ncfback.controller;

import com.example.ncfback.dto.ApiResponse;
import com.example.ncfback.dto.ListeningEventRequest;
import com.example.ncfback.dto.PageResponse;
import com.example.ncfback.entity.ListeningHistoryView;
import com.example.ncfback.service.ListeningHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/history")
@RequiredArgsConstructor
public class ListeningHistoryController {

    private final ListeningHistoryService listeningHistoryService;

    @GetMapping
    public ApiResponse<PageResponse<ListeningHistoryView>> page(
            @RequestParam(required = false, name = "userId") String userKey,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(listeningHistoryService.pageByUserKey(userKey, page, size));
    }

    @GetMapping("/users/{userId}")
    public ApiResponse<PageResponse<ListeningHistoryView>> pageByUser(
            @PathVariable String userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(listeningHistoryService.pageByUserKey(userId, page, size));
    }

    @PostMapping("/users/{userId}/items/{itemId}/play-start")
    public ApiResponse<Boolean> recordPlayStart(@PathVariable Long userId,
                                                @PathVariable Long itemId,
                                                @RequestBody(required = false) ListeningEventRequest request) {
        listeningHistoryService.recordPlayStart(userId, itemId, request);
        return ApiResponse.ok(true);
    }

    @PostMapping("/users/{userId}/items/{itemId}/play-complete")
    public ApiResponse<Boolean> recordPlayComplete(@PathVariable Long userId,
                                                   @PathVariable Long itemId,
                                                   @RequestBody(required = false) ListeningEventRequest request) {
        listeningHistoryService.recordPlayComplete(userId, itemId, request);
        return ApiResponse.ok(true);
    }
}

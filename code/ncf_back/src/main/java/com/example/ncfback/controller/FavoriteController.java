package com.example.ncfback.controller;

import com.example.ncfback.dto.ApiResponse;
import com.example.ncfback.entity.FavoriteItemView;
import com.example.ncfback.service.FavoriteService;
import com.example.ncfback.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/favorites")
@RequiredArgsConstructor
public class FavoriteController {

    private final FavoriteService favoriteService;
    private final UserService userService;

    @GetMapping("/users/{userId}")
    public ApiResponse<List<FavoriteItemView>> list(@PathVariable String userId) {
        Long resolvedUserId = userService.resolveUserId(userId);
        return ApiResponse.ok(favoriteService.listByUserId(resolvedUserId));
    }

    @GetMapping("/users/{userId}/items/{itemId}/exists")
    public ApiResponse<Boolean> exists(@PathVariable String userId, @PathVariable Long itemId) {
        Long resolvedUserId = userService.resolveUserId(userId);
        return ApiResponse.ok(favoriteService.isFavorite(resolvedUserId, itemId));
    }

    @PostMapping("/users/{userId}/items/{itemId}")
    public ApiResponse<Boolean> add(@PathVariable String userId, @PathVariable Long itemId) {
        Long resolvedUserId = userService.resolveUserId(userId);
        favoriteService.addFavorite(resolvedUserId, itemId);
        return ApiResponse.ok(true);
    }

    @DeleteMapping("/users/{userId}/items/{itemId}")
    public ApiResponse<Boolean> remove(@PathVariable String userId, @PathVariable Long itemId) {
        Long resolvedUserId = userService.resolveUserId(userId);
        favoriteService.removeFavorite(resolvedUserId, itemId);
        return ApiResponse.ok(true);
    }
}

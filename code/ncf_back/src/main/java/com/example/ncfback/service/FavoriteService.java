package com.example.ncfback.service;

import com.example.ncfback.entity.FavoriteItemView;
import com.example.ncfback.entity.ItemMedia;
import com.example.ncfback.mapper.FavoriteMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FavoriteService {

    private final FavoriteMapper favoriteMapper;
    private final UserService userService;
    private final ItemService itemService;
    private final MediaService mediaService;
    private final RecommendationService recommendationService;
    private final UserCacheService userCacheService;

    public List<FavoriteItemView> listByUserId(Long userId) {
        userService.getById(userId);
        if (favoriteMapper.countByUserId(userId) <= 0) {
            userCacheService.delayedDoubleDeleteFavorites(userId);
            return List.of();
        }
        return userCacheService.getFavoriteItems(userId, () -> loadFavoritesFromDatabase(userId));
    }

    private List<FavoriteItemView> loadFavoritesFromDatabase(Long userId) {
        List<FavoriteItemView> favorites = favoriteMapper.findByUserId(userId);
        if (favorites.isEmpty()) {
            return favorites;
        }

        Map<Long, ItemMedia> mediaByItemId = mediaService.getPreviewByItemIds(
                        favorites.stream().map(FavoriteItemView::getItemId).toList())
                .stream()
                .collect(Collectors.toMap(ItemMedia::getItemId, Function.identity()));

        for (FavoriteItemView favorite : favorites) {
            ItemMedia media = mediaByItemId.get(favorite.getItemId());
            if (media == null) {
                continue;
            }
            favorite.setPreviewUrl(media.getPreviewUrl());
            if (media.getCoverUrl() != null) {
                favorite.setCoverUrl(media.getCoverUrl());
            }
        }
        return favorites;
    }

    public boolean isFavorite(Long userId, Long itemId) {
        userService.getById(userId);
        itemService.getById(itemId);
        return favoriteMapper.exists(userId, itemId) > 0;
    }

    @Transactional
    public void addFavorite(Long userId, Long itemId) {
        userCacheService.delayedDoubleDeleteFavorites(userId);
        userService.getById(userId);
        itemService.getById(itemId);
        if (favoriteMapper.exists(userId, itemId) > 0) {
            return;
        }
        int affected = favoriteMapper.insert(userId, itemId);
        if (affected <= 0) {
            throw new IllegalArgumentException("Add favorite failed");
        }
        recommendationService.refreshUserRecommendations(userId);
    }

    @Transactional
    public void removeFavorite(Long userId, Long itemId) {
        userCacheService.delayedDoubleDeleteFavorites(userId);
        userService.getById(userId);
        itemService.getById(itemId);
        favoriteMapper.delete(userId, itemId);
        recommendationService.refreshUserRecommendations(userId);
    }
}

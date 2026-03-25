package com.example.ncfback.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class FavoriteItemView {
    private Long userId;
    private Long itemId;
    private String title;
    private String artistName;
    private String albumName;
    private String genreCode;
    private String coverUrl;
    private String previewUrl;
    private LocalDateTime favoriteTime;
}

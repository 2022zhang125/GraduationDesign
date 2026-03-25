package com.example.ncfback.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ItemMedia {
    private Long itemId;
    private String previewUrl;
    private String musicUrl;
    private String playUrl;
    private String coverUrl;
    private Integer previewDurationSeconds;
    private String lyricSnippet;
    private String sourcePlatform;
    private LocalDateTime updatedAt;
}

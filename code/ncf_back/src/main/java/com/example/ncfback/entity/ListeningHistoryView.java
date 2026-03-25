package com.example.ncfback.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ListeningHistoryView {
    private Long userId;
    private String username;
    private Long itemId;
    private String title;
    private String artistName;
    private String albumName;
    private String genreCode;
    private String coverUrl;
    private String previewUrl;
    private LocalDateTime lastListenTime;
    private Integer playCount;
    private Integer completePlayCount;
    private Integer playedDurationSeconds;
    private Double maxWatchRatio;
}

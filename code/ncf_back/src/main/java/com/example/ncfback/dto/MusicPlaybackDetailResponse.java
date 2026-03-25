package com.example.ncfback.dto;

import lombok.Data;

@Data
public class MusicPlaybackDetailResponse {
    private String source;
    private String query;
    private Integer trackIndex;
    private String qualityLevel;
    private String playUrl;
    private String title;
    private String artistName;
    private String albumName;
    private String musicUrl;
    private String coverUrl;
    private String lyricsText;
    private String sourcePlatform;
}

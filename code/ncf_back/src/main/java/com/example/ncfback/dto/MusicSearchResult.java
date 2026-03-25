package com.example.ncfback.dto;

import lombok.Data;

@Data
public class MusicSearchResult {
    private String query;
    private String source;
    private String sourceLabel;
    private Integer trackIndex;
    private String qualityLevel;
    private Long itemId;
    private String title;
    private String albumName;
    private String artistName;
    private String lyricSnippet;
    private String payTag;
    private String musicUrl;
    private String coverUrl;
    private String playUrl;
    private boolean inCatalog;
    private String sourcePlatform;
}

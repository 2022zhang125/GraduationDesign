package com.example.ncfback.dto;

import lombok.Data;

@Data
public class LyricsLookupResponse {
    private Long itemId;
    private String title;
    private String albumName;
    private String artistName;
    private String lyricsText;
    private String coverUrl;
    private String source;
}

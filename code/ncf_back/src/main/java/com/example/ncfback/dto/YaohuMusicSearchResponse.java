package com.example.ncfback.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class YaohuMusicSearchResponse {
    private Integer code;
    private String msg;
    private String query;
    private String source;
    private String tips;
    private List<SongOption> songs = new ArrayList<>();

    @Data
    public static class SongOption {
        private Integer index;
        private String name;
        private String singer;
        private String album;
        private String payTag;
        private String mid;
        private String musicUrl;
        private String coverUrl;
        private String lyricText;
    }
}

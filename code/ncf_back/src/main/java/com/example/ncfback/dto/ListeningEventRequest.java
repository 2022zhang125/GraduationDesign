package com.example.ncfback.dto;

import lombok.Data;

@Data
public class ListeningEventRequest {
    private String requestId;
    private String source;
    private Integer playedSeconds;
    private Integer totalPlayedSeconds;
    private Integer durationSeconds;
    private Boolean completed;
}

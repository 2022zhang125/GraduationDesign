package com.example.ncfback.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ListeningInteractionRecord {
    private String requestId;
    private String traceId;
    private Long userId;
    private Long itemId;
    private String eventType;
    private LocalDateTime eventTime;
    private Integer playDurationSec;
    private Double watchRatio;
    private String deviceType;
    private String channel;
    private Integer positionNo;
    private String contextJson;
}

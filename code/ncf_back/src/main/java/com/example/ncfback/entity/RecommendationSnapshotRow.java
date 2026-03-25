package com.example.ncfback.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RecommendationSnapshotRow {
    private String requestId;
    private Long userId;
    private Long itemId;
    private String modelVersion;
    private Double score;
    private Integer rankNo;
    private String reasonText;
    private String scene;
    private LocalDateTime requestTime;
}

package com.example.ncfback.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RecommendationExposureRow {
    private Long itemId;
    private Integer exposureCount;
    private LocalDateTime lastRequestTime;
}

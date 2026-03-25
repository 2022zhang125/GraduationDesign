package com.example.ncfback.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RecommendationView {
    private Long itemId;
    private String title;
    private String artistName;
    private String genreCode;
    private Double score;
    private Integer rankNo;
    private String reasonText;
    private LocalDateTime requestTime;
}

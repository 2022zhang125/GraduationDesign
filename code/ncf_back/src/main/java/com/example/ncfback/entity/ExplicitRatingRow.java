package com.example.ncfback.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ExplicitRatingRow {
    private Long userId;
    private Long itemId;
    private Double ratingValue;
    private LocalDateTime ratingTime;
}

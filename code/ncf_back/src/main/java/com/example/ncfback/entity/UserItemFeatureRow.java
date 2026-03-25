package com.example.ncfback.entity;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class UserItemFeatureRow {
    private LocalDate featureDate;
    private Long userId;
    private Long itemId;
    private LocalDateTime lastInteractionTime;
    private Integer interactionCnt30d;
    private Integer completePlayCnt30d;
    private Integer favoriteFlagEver;
    private Integer dislikeFlag30d;
}

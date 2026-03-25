package com.example.ncfback.entity;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class Item {
    private Long itemId;
    private String externalItemNo;
    private String title;
    private Long artistId;
    private String artistName;
    private Long albumId;
    private String albumName;
    private String genreCode;
    private String languageCode;
    private Integer durationSeconds;
    private LocalDate releaseDate;
    private Integer itemStatus;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

package com.example.ncfback.entity;

import lombok.Data;

@Data
public class UserEmbeddingRow {
    private String modelVersion;
    private Long userId;
    private Integer embeddingDim;
    private String embeddingVector;
}

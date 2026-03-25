package com.example.ncfback.entity;

import lombok.Data;

@Data
public class ItemEmbeddingRow {
    private String modelVersion;
    private Long itemId;
    private Integer embeddingDim;
    private String embeddingVector;
}

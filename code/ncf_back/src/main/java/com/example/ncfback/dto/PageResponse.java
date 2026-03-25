package com.example.ncfback.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class PageResponse<T> {
    private List<T> list;
    private long total;
    private int page;
    private int size;
}

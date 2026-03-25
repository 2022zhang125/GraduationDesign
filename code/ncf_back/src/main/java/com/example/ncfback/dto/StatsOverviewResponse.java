package com.example.ncfback.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class StatsOverviewResponse {
    private long userCount;
    private long itemCount;
    private long interactionCount;
    private long trainingSampleCount;
    private long recommendationCount;
}

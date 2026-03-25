package com.example.ncfback.service;

import com.example.ncfback.dto.StatsOverviewResponse;
import com.example.ncfback.mapper.RecommendationMapper;
import com.example.ncfback.mapper.StatsMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class StatsService {

    private final StatsMapper statsMapper;
    private final RecommendationMapper recommendationMapper;

    public StatsOverviewResponse overview() {
        return new StatsOverviewResponse(
                statsMapper.countUsers(),
                statsMapper.countItems(),
                statsMapper.countInteractions(),
                statsMapper.countTrainingSamples(),
                recommendationMapper.countAll()
        );
    }
}

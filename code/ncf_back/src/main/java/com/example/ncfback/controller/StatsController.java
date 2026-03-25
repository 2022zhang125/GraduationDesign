package com.example.ncfback.controller;

import com.example.ncfback.dto.ApiResponse;
import com.example.ncfback.dto.StatsOverviewResponse;
import com.example.ncfback.service.StatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stats")
@RequiredArgsConstructor
public class StatsController {

    private final StatsService statsService;

    @GetMapping("/overview")
    public ApiResponse<StatsOverviewResponse> overview() {
        return ApiResponse.ok(statsService.overview());
    }
}

package com.example.ncfback.controller;

import com.example.ncfback.dto.ApiResponse;
import com.example.ncfback.entity.Item;
import com.example.ncfback.entity.RecommendationView;
import com.example.ncfback.entity.User;
import com.example.ncfback.service.AiExplainService;
import com.example.ncfback.service.ItemService;
import com.example.ncfback.service.RecommendationService;
import com.example.ncfback.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/recommendations")
@RequiredArgsConstructor
public class RecommendationController {

    private final RecommendationService recommendationService;
    private final UserService userService;
    private final ItemService itemService;
    private final AiExplainService aiExplainService;

    @GetMapping("/users/{userId}")
    public ApiResponse<List<RecommendationView>> userRecommendations(
            @PathVariable String userId,
            @RequestParam(defaultValue = "10") int limit) {
        Long resolvedUserId = userService.resolveUserId(userId);
        return ApiResponse.ok(recommendationService.getRecommendations(resolvedUserId, limit));
    }

    @GetMapping("/users/{userId}/items/{itemId}/explanation")
    public ApiResponse<String> explainRecommendation(
            @PathVariable String userId,
            @PathVariable Long itemId,
            @RequestParam(defaultValue = "10") int limit) {
        Long resolvedUserId = userService.resolveUserId(userId);

        User user = userService.getById(resolvedUserId);
        Item item = itemService.getById(itemId);

        RecommendationView matched = recommendationService.getRecommendations(resolvedUserId, limit)
                .stream()
                .filter(r -> r.getItemId().equals(itemId))
                .findFirst()
                .orElseGet(() -> {
                    RecommendationView fallback = new RecommendationView();
                    fallback.setItemId(itemId);
                    fallback.setTitle(item.getTitle());
                    fallback.setArtistName(item.getArtistName());
                    fallback.setGenreCode(item.getGenreCode());
                    fallback.setScore(0.0);
                    fallback.setRankNo(0);
                    fallback.setReasonText("未命中推荐列表，基于内容生成解释");
                    return fallback;
                });

        return ApiResponse.ok(aiExplainService.explain(user, item, matched));
    }
}

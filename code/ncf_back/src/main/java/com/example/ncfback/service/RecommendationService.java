package com.example.ncfback.service;

import com.example.ncfback.config.RecommendationRefreshProperties;
import com.example.ncfback.entity.RecommendationExposureRow;
import com.example.ncfback.entity.RecommendationView;
import com.example.ncfback.mapper.FavoriteMapper;
import com.example.ncfback.mapper.RecommendationFeatureMapper;
import com.example.ncfback.mapper.UserFeedbackMapper;
import com.example.ncfback.mapper.UserFollowMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class RecommendationService {

    private static final int TARGET_RECOMMENDATION_SIZE = 10;
    private static final ZoneId APP_ZONE_ID = ZoneId.of("Asia/Shanghai");

    private final FavoriteMapper favoriteMapper;
    private final RecommendationFeatureMapper recommendationFeatureMapper;
    private final NcfAttentionRecommendationEngine recommendationEngine;
    private final UserCacheService userCacheService;
    private final UserService userService;
    private final UserFollowMapper userFollowMapper;
    private final UserFeedbackMapper userFeedbackMapper;
    private final RecommendationRefreshProperties properties;

    public List<RecommendationView> getRecommendations(Long userId, int limit) {
        userService.getById(userId);
        int safeLimit = Math.min(Math.max(limit, 1), TARGET_RECOMMENDATION_SIZE);
        List<RecommendationView> recommendations = userCacheService.getDailyRecommendations(
                userId,
                () -> isColdStartUser(userId)
                        ? loadColdStartRecommendations(userId, TARGET_RECOMMENDATION_SIZE)
                        : loadRecommendationsFromDatabase(userId)
        );
        return recommendations.stream()
                .limit(safeLimit)
                .toList();
    }

    private List<RecommendationView> loadRecommendationsFromDatabase(Long userId) {
        List<Long> favoriteItemIds = favoriteMapper.findItemIdsByUserId(userId);
        List<RecommendationView> candidates = recommendationEngine.refreshUserSnapshot(
                        userId,
                        Math.max(TARGET_RECOMMENDATION_SIZE, properties.getCandidatePoolSize()))
                .stream()
                .filter(view -> !favoriteItemIds.contains(view.getItemId()))
                .toList();
        return rerankDailyRecommendations(userId, candidates, TARGET_RECOMMENDATION_SIZE);
    }

    @Transactional
    public void refreshUserRecommendations(Long userId) {
        userCacheService.delayedDoubleDeleteRecommendations(userId);
        if (isColdStartUser(userId)) {
            recommendationFeatureMapper.deleteRecommendationsByUserId(userId);
            return;
        }
        recommendationEngine.refreshUserSnapshot(userId, Math.max(TARGET_RECOMMENDATION_SIZE, properties.getCandidatePoolSize()));
    }

    @Transactional
    public RefreshSummary refreshAllSnapshots() {
        userCacheService.delayedDoubleDeleteAllRecommendations();
        return recommendationEngine.refreshAllSnapshots();
    }

    private boolean isColdStartUser(Long userId) {
        long listeningCount = recommendationFeatureMapper.countListeningInteractionsByUserId(userId);
        long ratingCount = recommendationFeatureMapper.countExplicitRatingsByUserId(userId);
        boolean hasFavorites = !favoriteMapper.findItemIdsByUserId(userId).isEmpty();
        long followingCount = userFollowMapper.countByFollowerUserId(userId);
        long feedbackCount = userFeedbackMapper.countActiveByUserId(userId);
        return listeningCount <= 0 && ratingCount <= 0 && !hasFavorites && followingCount <= 0 && feedbackCount <= 0;
    }

    private List<RecommendationView> loadColdStartRecommendations(Long userId, int limit) {
        List<RecommendationView> topItems = recommendationFeatureMapper.findColdStartTopItems(
                Math.max(limit, properties.getCandidatePoolSize()));
        LocalDateTime now = LocalDateTime.now(APP_ZONE_ID);
        for (int index = 0; index < topItems.size(); index++) {
            RecommendationView view = topItems.get(index);
            view.setRankNo(index + 1);
            view.setScore(normalizeHotScore(view.getScore()));
            view.setReasonText("当前处于新用户冷启动阶段，先展示平台热门 Top10 歌曲；当你产生真实播放、收藏或评分后，系统会切换到个性化推荐。");
            view.setRequestTime(now);
        }
        return rerankDailyRecommendations(userId, topItems, limit);
    }

    private List<RecommendationView> rerankDailyRecommendations(Long userId,
                                                                List<RecommendationView> candidates,
                                                                int limit) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }

        LocalDate today = LocalDate.now(APP_ZONE_ID);
        LocalDateTime todayStart = today.atStartOfDay();
        LocalDateTime historyStart = today.minusDays(Math.max(1, properties.getFreshnessWindowDays())).atStartOfDay();

        Map<Long, RecommendationExposureRow> exposureByItemId = new LinkedHashMap<>();
        if (userId != null) {
            for (RecommendationExposureRow exposure : recommendationFeatureMapper.findRecentRecommendationExposure(
                    userId, historyStart, todayStart)) {
                exposureByItemId.put(exposure.getItemId(), exposure);
            }
        }

        List<RecommendationView> pool = candidates.stream()
                .map(view -> applyDailyAdjustments(userId, view, exposureByItemId.get(view.getItemId()), today))
                .sorted(Comparator.comparingDouble((RecommendationView view) -> safeScore(view.getScore())).reversed()
                        .thenComparing(RecommendationView::getItemId))
                .limit(Math.max(limit, properties.getCandidatePoolSize()))
                .toList();

        List<RecommendationView> remaining = new ArrayList<>(pool);
        List<RecommendationView> selected = new ArrayList<>();
        Map<String, Integer> selectedArtistCounts = new LinkedHashMap<>();
        Map<String, Integer> selectedGenreCounts = new LinkedHashMap<>();

        while (!remaining.isEmpty() && selected.size() < limit) {
            RecommendationView best = null;
            double bestScore = Double.NEGATIVE_INFINITY;

            for (RecommendationView candidate : remaining) {
                double rerankScore = safeScore(candidate.getScore())
                        - (selectedArtistCounts.getOrDefault(normalizeKey(candidate.getArtistName()), 0)
                        * properties.getRepeatedArtistPenalty())
                        - (selectedGenreCounts.getOrDefault(normalizeKey(candidate.getGenreCode()), 0)
                        * properties.getRepeatedGenrePenalty())
                        + dailyExplorationNoise(userId, candidate.getItemId(), today, selected.size());

                if (rerankScore > bestScore) {
                    best = candidate;
                    bestScore = rerankScore;
                }
            }

            if (best == null) {
                break;
            }

            remaining.remove(best);
            RecommendationView output = copyRecommendation(best);
            output.setRankNo(selected.size() + 1);
            output.setScore(roundScore(clamp(bestScore, 0.0d, 1.0d)));
            selected.add(output);
            selectedArtistCounts.merge(normalizeKey(best.getArtistName()), 1, Integer::sum);
            selectedGenreCounts.merge(normalizeKey(best.getGenreCode()), 1, Integer::sum);
        }

        return selected;
    }

    private RecommendationView applyDailyAdjustments(Long userId,
                                                     RecommendationView source,
                                                     RecommendationExposureRow exposure,
                                                     LocalDate today) {
        RecommendationView adjusted = copyRecommendation(source);
        double score = safeScore(source.getScore());
        double exposurePenalty = calculateExposurePenalty(exposure, today);
        double explorationNoise = dailyExplorationNoise(userId, source.getItemId(), today, 0);
        adjusted.setScore(roundScore(clamp(score - exposurePenalty + explorationNoise, 0.0d, 1.0d)));
        return adjusted;
    }

    private double calculateExposurePenalty(RecommendationExposureRow exposure, LocalDate today) {
        if (exposure == null) {
            return 0.0d;
        }
        int exposureCount = exposure.getExposureCount() == null ? 0 : Math.max(0, exposure.getExposureCount());
        double frequencyPenalty = Math.min(0.28d, exposureCount * properties.getRepeatedItemPenalty());
        LocalDate lastDate = exposure.getLastRequestTime() == null
                ? today.minusDays(properties.getFreshnessWindowDays())
                : exposure.getLastRequestTime().toLocalDate();
        long daysSince = Math.max(1L, ChronoUnit.DAYS.between(lastDate, today));
        double recencyPenalty = Math.exp(-(daysSince - 1) / 2.5d) * 0.12d;
        return Math.min(0.35d, frequencyPenalty + recencyPenalty);
    }

    private double dailyExplorationNoise(Long userId, Long itemId, LocalDate date, int slot) {
        long seed = 1469598103934665603L;
        seed ^= (userId == null ? 0L : userId);
        seed *= 1099511628211L;
        seed ^= (itemId == null ? 0L : itemId);
        seed *= 1099511628211L;
        seed ^= date.toEpochDay();
        seed *= 1099511628211L;
        seed ^= slot;
        seed *= 1099511628211L;
        long mixed = mix64(seed);
        double normalized = ((mixed >>> 11) & 0x1FFFFFL) / (double) 0x1FFFFF;
        return ((normalized * 2.0d) - 1.0d) * properties.getDailyExplorationJitter();
    }

    private long mix64(long value) {
        value ^= (value >>> 33);
        value *= 0xff51afd7ed558ccdL;
        value ^= (value >>> 33);
        value *= 0xc4ceb9fe1a85ec53L;
        value ^= (value >>> 33);
        return value;
    }

    private RecommendationView copyRecommendation(RecommendationView source) {
        RecommendationView copy = new RecommendationView();
        copy.setItemId(source.getItemId());
        copy.setTitle(source.getTitle());
        copy.setArtistName(source.getArtistName());
        copy.setGenreCode(source.getGenreCode());
        copy.setScore(source.getScore());
        copy.setRankNo(source.getRankNo());
        copy.setReasonText(source.getReasonText());
        copy.setRequestTime(source.getRequestTime());
        return copy;
    }

    private double safeScore(Double value) {
        return value == null ? 0.0d : value;
    }

    private double roundScore(double value) {
        return Math.round(value * 1_000_000d) / 1_000_000d;
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private String normalizeKey(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private double normalizeHotScore(Double rawPopularity) {
        double value = rawPopularity == null ? 0.0d : Math.max(0.0d, rawPopularity);
        if (value <= 0.0d) {
            return 0.5d;
        }
        return Math.round((value / (value + 1.0d)) * 1_000_000d) / 1_000_000d;
    }

    public record RefreshSummary(int userCount, int recommendationCount, String modelVersion) {
    }
}

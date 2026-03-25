package com.example.ncfback.service;

import com.example.ncfback.config.RecommendationRefreshProperties;
import com.example.ncfback.entity.ExplicitRatingRow;
import com.example.ncfback.entity.FavoriteEdge;
import com.example.ncfback.entity.Item;
import com.example.ncfback.entity.ItemEmbeddingRow;
import com.example.ncfback.entity.RecommendationSnapshotRow;
import com.example.ncfback.entity.RecommendationView;
import com.example.ncfback.entity.User;
import com.example.ncfback.entity.UserFeedbackTicket;
import com.example.ncfback.entity.UserEmbeddingRow;
import com.example.ncfback.entity.UserFollowEdge;
import com.example.ncfback.entity.UserItemFeatureRow;
import com.example.ncfback.mapper.RecommendationFeatureMapper;
import com.example.ncfback.mapper.UserFeedbackMapper;
import com.example.ncfback.mapper.UserFollowMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class NcfAttentionRecommendationEngine {

    private static final int EMBEDDING_DIM = 8;
    private static final double EPSILON = 1e-9;
    private static final ZoneId APP_ZONE_ID = ZoneId.of("Asia/Shanghai");

    private final RecommendationFeatureMapper recommendationFeatureMapper;
    private final UserFollowMapper userFollowMapper;
    private final UserFeedbackMapper userFeedbackMapper;
    private final RecommendationRefreshProperties properties;
    private final ObjectMapper objectMapper;

    @Transactional
    public RecommendationService.RefreshSummary refreshAllSnapshots() {
        RecommendationDataset dataset = prepareDataset(true);
        int recommendationCount = 0;
        for (User user : dataset.users().values()) {
            List<RecommendationView> recommendations = scoreUser(user.getUserId(), properties.getTopK(), dataset);
            persistRecommendations(user.getUserId(), recommendations, dataset.modelVersion());
            recommendationCount += recommendations.size();
        }
        return new RecommendationService.RefreshSummary(
                dataset.users().size(),
                recommendationCount,
                dataset.modelVersion()
        );
    }

    @Transactional
    public List<RecommendationView> refreshUserSnapshot(Long userId, int limit) {
        RecommendationDataset dataset = prepareUserDataset(userId);
        List<RecommendationView> recommendations = scoreUser(userId, limit, dataset);
        persistRecommendations(userId, recommendations, dataset.modelVersion());
        return recommendations;
    }

    private RecommendationDataset prepareUserDataset(Long userId) {
        String targetModelVersion = properties.getModelVersion();
        String sourceModelVersion = recommendationFeatureMapper.findLatestReadyModelVersion();
        if (sourceModelVersion == null || sourceModelVersion.isBlank()) {
            sourceModelVersion = targetModelVersion;
        }

        ensureModelRegistry(targetModelVersion);

        User user = recommendationFeatureMapper.findActiveUserById(userId);
        if (user == null) {
            throw new IllegalArgumentException("User not found: " + userId);
        }

        Map<Long, User> users = new LinkedHashMap<>();
        users.put(userId, user);

        Map<Long, Item> items = new LinkedHashMap<>();
        for (Item item : recommendationFeatureMapper.findAllActiveItems()) {
            items.put(item.getItemId(), item);
        }

        Map<Long, Map<Long, HistorySignal>> historyByUser = buildHistorySignals(
                recommendationFeatureMapper.findLatestUserItemFeaturesByUserId(userId),
                recommendationFeatureMapper.findExplicitRatingsByUserId(userId),
                recommendationFeatureMapper.findFavoritesByUserId(userId),
                items
        );
        Map<Long, Map<Long, SocialSignal>> socialSignalsByUser = buildSocialSignals(
                toFollowEdgeMap(userFollowMapper.findEdgesByFollowerUserId(userId)),
                historyByUser,
                items
        );
        Map<Long, FeedbackProfile> feedbackProfiles = buildFeedbackProfiles(
                userFeedbackMapper.findActiveByUserId(userId)
        );

        Map<Long, double[]> sourceItemEmbeddings = toItemEmbeddingMap(
                recommendationFeatureMapper.findItemEmbeddings(sourceModelVersion)
        );
        Map<Long, double[]> itemEmbeddings = new LinkedHashMap<>();
        for (Item item : items.values()) {
            itemEmbeddings.put(item.getItemId(), buildItemEmbedding(item, sourceItemEmbeddings.get(item.getItemId())));
        }

        Map<Long, double[]> userEmbeddings = new LinkedHashMap<>();
        UserEmbeddingRow userEmbeddingRow = recommendationFeatureMapper.findUserEmbeddingByUserId(sourceModelVersion, userId);
        double[] sourceUserEmbedding = userEmbeddingRow == null ? null : parseVector(userEmbeddingRow.getEmbeddingVector());
        Map<Long, HistorySignal> historySignals = historyByUser.getOrDefault(userId, Map.of());
        Map<Long, SocialSignal> socialSignals = socialSignalsByUser.getOrDefault(userId, Map.of());
        userEmbeddings.put(
                userId,
                buildUserEmbedding(
                        user,
                        sourceUserEmbedding,
                        historySignals.values(),
                        socialSignals.values(),
                        feedbackProfiles.get(userId),
                        itemEmbeddings
                )
        );

        return new RecommendationDataset(
                targetModelVersion,
                users,
                items,
                itemEmbeddings,
                userEmbeddings,
                historyByUser,
                socialSignalsByUser,
                feedbackProfiles
        );
    }

    private RecommendationDataset prepareDataset(boolean persistEmbeddings) {
        String targetModelVersion = properties.getModelVersion();
        String sourceModelVersion = recommendationFeatureMapper.findLatestReadyModelVersion();
        if (sourceModelVersion == null || sourceModelVersion.isBlank()) {
            sourceModelVersion = targetModelVersion;
        }

        ensureModelRegistry(targetModelVersion);

        Map<Long, User> users = new LinkedHashMap<>();
        for (User user : recommendationFeatureMapper.findAllActiveUsers()) {
            users.put(user.getUserId(), user);
        }

        Map<Long, Item> items = new LinkedHashMap<>();
        for (Item item : recommendationFeatureMapper.findAllActiveItems()) {
            items.put(item.getItemId(), item);
        }

        Map<Long, Map<Long, HistorySignal>> historyByUser = buildHistorySignals(
                recommendationFeatureMapper.findLatestUserItemFeatures(),
                recommendationFeatureMapper.findExplicitRatings(),
                recommendationFeatureMapper.findAllFavorites(),
                items
        );
        Map<Long, Map<Long, SocialSignal>> socialSignalsByUser = buildSocialSignals(
                toFollowEdgeMap(userFollowMapper.findAllEdges()),
                historyByUser,
                items
        );
        Map<Long, FeedbackProfile> feedbackProfiles = buildFeedbackProfiles(userFeedbackMapper.findAllActive());

        Map<Long, double[]> sourceItemEmbeddings = toItemEmbeddingMap(
                recommendationFeatureMapper.findItemEmbeddings(sourceModelVersion)
        );
        Map<Long, double[]> sourceUserEmbeddings = toUserEmbeddingMap(
                recommendationFeatureMapper.findUserEmbeddings(sourceModelVersion)
        );

        Map<Long, double[]> itemEmbeddings = new LinkedHashMap<>();
        List<ItemEmbeddingRow> itemRows = new ArrayList<>();
        for (Item item : items.values()) {
            double[] vector = buildItemEmbedding(item, sourceItemEmbeddings.get(item.getItemId()));
            itemEmbeddings.put(item.getItemId(), vector);
            if (persistEmbeddings) {
                ItemEmbeddingRow row = new ItemEmbeddingRow();
                row.setModelVersion(targetModelVersion);
                row.setItemId(item.getItemId());
                row.setEmbeddingDim(EMBEDDING_DIM);
                row.setEmbeddingVector(toJson(vector));
                itemRows.add(row);
            }
        }

        Map<Long, double[]> userEmbeddings = new LinkedHashMap<>();
        List<UserEmbeddingRow> userRows = new ArrayList<>();
        for (User user : users.values()) {
            Map<Long, HistorySignal> historySignals = historyByUser.getOrDefault(user.getUserId(), Map.of());
            Map<Long, SocialSignal> socialSignals = socialSignalsByUser.getOrDefault(user.getUserId(), Map.of());
            double[] vector = buildUserEmbedding(
                    user,
                    sourceUserEmbeddings.get(user.getUserId()),
                    historySignals.values(),
                    socialSignals.values(),
                    feedbackProfiles.get(user.getUserId()),
                    itemEmbeddings
            );
            userEmbeddings.put(user.getUserId(), vector);
            if (persistEmbeddings) {
                UserEmbeddingRow row = new UserEmbeddingRow();
                row.setModelVersion(targetModelVersion);
                row.setUserId(user.getUserId());
                row.setEmbeddingDim(EMBEDDING_DIM);
                row.setEmbeddingVector(toJson(vector));
                userRows.add(row);
            }
        }

        if (persistEmbeddings) {
            if (!itemRows.isEmpty()) {
                recommendationFeatureMapper.upsertItemEmbeddings(itemRows);
            }
            if (!userRows.isEmpty()) {
                recommendationFeatureMapper.upsertUserEmbeddings(userRows);
            }
        }

        return new RecommendationDataset(
                targetModelVersion,
                users,
                items,
                itemEmbeddings,
                userEmbeddings,
                historyByUser,
                socialSignalsByUser,
                feedbackProfiles
        );
    }

    private Map<Long, Map<Long, HistorySignal>> buildHistorySignals(List<UserItemFeatureRow> features,
                                                                    List<ExplicitRatingRow> ratings,
                                                                    List<FavoriteEdge> favorites,
                                                                    Map<Long, Item> items) {
        Map<Long, Map<Long, HistorySignal>> signalsByUser = new LinkedHashMap<>();

        for (UserItemFeatureRow row : features) {
            if (!items.containsKey(row.getItemId())) {
                continue;
            }
            HistorySignal signal = signalsByUser
                    .computeIfAbsent(row.getUserId(), key -> new LinkedHashMap<>())
                    .computeIfAbsent(row.getItemId(), HistorySignal::new);
            signal.setInteractionCount(safeInt(row.getInteractionCnt30d()));
            signal.setCompletePlayCount(safeInt(row.getCompletePlayCnt30d()));
            signal.setFavorite(signal.isFavorite() || safeInt(row.getFavoriteFlagEver()) > 0);
            signal.setDislike(signal.isDislike() || safeInt(row.getDislikeFlag30d()) > 0);
            signal.setLastInteractionTime(latestOf(signal.getLastInteractionTime(), row.getLastInteractionTime()));
        }

        for (FavoriteEdge favorite : favorites) {
            if (!items.containsKey(favorite.getItemId())) {
                continue;
            }
            HistorySignal signal = signalsByUser
                    .computeIfAbsent(favorite.getUserId(), key -> new LinkedHashMap<>())
                    .computeIfAbsent(favorite.getItemId(), HistorySignal::new);
            signal.setFavorite(true);
            if (signal.getLastInteractionTime() == null) {
                signal.setLastInteractionTime(LocalDateTime.now(APP_ZONE_ID).minusDays(3));
            }
        }

        for (ExplicitRatingRow rating : ratings) {
            if (!items.containsKey(rating.getItemId())) {
                continue;
            }
            HistorySignal signal = signalsByUser
                    .computeIfAbsent(rating.getUserId(), key -> new LinkedHashMap<>())
                    .computeIfAbsent(rating.getItemId(), HistorySignal::new);
            signal.setExplicitRating(Math.max(
                    signal.getExplicitRating(),
                    rating.getRatingValue() == null ? 0.0d : rating.getRatingValue()
            ));
            signal.setLastInteractionTime(latestOf(signal.getLastInteractionTime(), rating.getRatingTime()));
        }

        return signalsByUser;
    }

    private Map<Long, List<UserFollowEdge>> toFollowEdgeMap(List<UserFollowEdge> edges) {
        Map<Long, List<UserFollowEdge>> followEdgesByUser = new LinkedHashMap<>();
        for (UserFollowEdge edge : edges) {
            if (edge.getFollowerUserId() == null || edge.getFolloweeUserId() == null) {
                continue;
            }
            followEdgesByUser
                    .computeIfAbsent(edge.getFollowerUserId(), key -> new ArrayList<>())
                    .add(edge);
        }
        return followEdgesByUser;
    }

    private Map<Long, Map<Long, SocialSignal>> buildSocialSignals(Map<Long, List<UserFollowEdge>> followEdgesByUser,
                                                                  Map<Long, Map<Long, HistorySignal>> historyByUser,
                                                                  Map<Long, Item> items) {
        Map<Long, Map<Long, SocialSignal>> socialSignalsByUser = new LinkedHashMap<>();
        for (Map.Entry<Long, List<UserFollowEdge>> entry : followEdgesByUser.entrySet()) {
            Long followerUserId = entry.getKey();
            Map<Long, SocialSignal> itemSignals = socialSignalsByUser.computeIfAbsent(
                    followerUserId,
                    key -> new LinkedHashMap<>()
            );
            for (UserFollowEdge edge : entry.getValue()) {
                Map<Long, HistorySignal> followeeSignals = historyByUser.getOrDefault(edge.getFolloweeUserId(), Map.of());
                for (HistorySignal followeeSignal : followeeSignals.values()) {
                    if (!items.containsKey(followeeSignal.getItemId())) {
                        continue;
                    }
                    double weight = friendPreferenceWeight(followeeSignal);
                    if (weight <= 0.0d) {
                        continue;
                    }
                    SocialSignal socialSignal = itemSignals
                            .computeIfAbsent(followeeSignal.getItemId(), SocialSignal::new);
                    socialSignal.absorb(edge, followeeSignal, weight);
                }
            }
        }
        return socialSignalsByUser;
    }

    private Map<Long, FeedbackProfile> buildFeedbackProfiles(List<UserFeedbackTicket> tickets) {
        Map<Long, FeedbackProfile> profiles = new LinkedHashMap<>();
        if (tickets == null || tickets.isEmpty()) {
            return profiles;
        }
        for (UserFeedbackTicket ticket : tickets) {
            if (ticket == null || ticket.getUserId() == null) {
                continue;
            }
            FeedbackProfile profile = profiles.computeIfAbsent(ticket.getUserId(), key -> new FeedbackProfile());
            double strength = clamp(
                    ((ticket.getPreferenceStrength() == null ? 3 : ticket.getPreferenceStrength()) / 5.0d),
                    0.20d,
                    1.0d
            );
            profile.absorb(ticket, strength);
        }
        return profiles;
    }

    private List<RecommendationView> scoreUser(Long userId, int limit, RecommendationDataset dataset) {
        User user = dataset.users().get(userId);
        if (user == null) {
            throw new IllegalArgumentException("User not found: " + userId);
        }

        int safeLimit = Math.min(Math.max(limit, 1), Math.max(properties.getTopK(), 1));
        Map<Long, HistorySignal> historySignals = dataset.historyByUser().getOrDefault(userId, Map.of());
        Map<Long, SocialSignal> socialSignals = dataset.socialSignalsByUser().getOrDefault(userId, Map.of());
        FeedbackProfile feedbackProfile = dataset.feedbackProfiles().get(userId);

        Map<String, Double> genrePreference = new LinkedHashMap<>();
        Map<String, Double> languagePreference = new LinkedHashMap<>();
        Map<String, Double> artistPreference = new LinkedHashMap<>();
        accumulatePreferences(historySignals.values(), dataset.items(), genrePreference, languagePreference, artistPreference);

        Map<String, Double> socialGenrePreference = new LinkedHashMap<>();
        Map<String, Double> socialLanguagePreference = new LinkedHashMap<>();
        Map<String, Double> socialArtistPreference = new LinkedHashMap<>();
        accumulateSocialPreferences(
                socialSignals.values(),
                dataset.items(),
                socialGenrePreference,
                socialLanguagePreference,
                socialArtistPreference
        );

        double[] userEmbedding = dataset.userEmbeddings().get(userId);
        List<CandidateScore> candidates = new ArrayList<>();

        for (Item item : dataset.items().values()) {
            HistorySignal existingSignal = historySignals.get(item.getItemId());
            if (existingSignal != null && (existingSignal.isFavorite() || existingSignal.isDislike())) {
                continue;
            }

            CandidateScore candidate = scoreCandidate(
                    item,
                    userEmbedding,
                    historySignals,
                    socialSignals,
                    dataset.items(),
                    dataset.itemEmbeddings(),
                    genrePreference,
                    languagePreference,
                    artistPreference,
                    socialGenrePreference,
                    socialLanguagePreference,
                    socialArtistPreference,
                    feedbackProfile
            );
            if (candidate.finalScore() > 0.0d) {
                candidates.add(candidate);
            }
        }

        candidates.sort(Comparator
                .comparingDouble(CandidateScore::finalScore).reversed()
                .thenComparing(CandidateScore::itemId));

        List<RecommendationView> recommendations = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now(APP_ZONE_ID);
        for (int index = 0; index < Math.min(safeLimit, candidates.size()); index++) {
            CandidateScore candidate = candidates.get(index);
            RecommendationView view = new RecommendationView();
            view.setItemId(candidate.itemId());
            view.setTitle(candidate.title());
            view.setArtistName(candidate.artistName());
            view.setGenreCode(candidate.genreCode());
            view.setScore(roundScore(candidate.finalScore()));
            view.setRankNo(index + 1);
            view.setReasonText(candidate.reasonText());
            view.setRequestTime(now);
            recommendations.add(view);
        }
        return recommendations;
    }

    private CandidateScore scoreCandidate(Item candidateItem,
                                          double[] userEmbedding,
                                          Map<Long, HistorySignal> historySignals,
                                          Map<Long, SocialSignal> socialSignals,
                                          Map<Long, Item> itemById,
                                          Map<Long, double[]> itemEmbeddings,
                                          Map<String, Double> genrePreference,
                                          Map<String, Double> languagePreference,
                                          Map<String, Double> artistPreference,
                                          Map<String, Double> socialGenrePreference,
                                          Map<String, Double> socialLanguagePreference,
                                          Map<String, Double> socialArtistPreference,
                                          FeedbackProfile feedbackProfile) {
        double[] candidateEmbedding = itemEmbeddings.get(candidateItem.getItemId());
        if (candidateEmbedding == null) {
            return CandidateScore.empty(candidateItem);
        }

        double latentScore = sigmoid(2.2d * cosine(userEmbedding, candidateEmbedding));
        List<AttentionContribution> contributions = new ArrayList<>();

        for (HistorySignal signal : historySignals.values()) {
            if (signal.getItemId().equals(candidateItem.getItemId())) {
                continue;
            }
            Item historyItem = itemById.get(signal.getItemId());
            double[] historyEmbedding = itemEmbeddings.get(signal.getItemId());
            if (historyItem == null || historyEmbedding == null) {
                continue;
            }

            double weight = preferenceWeight(signal);
            if (weight <= 0.05d) {
                continue;
            }

            double similarity = (cosine(candidateEmbedding, historyEmbedding) + 1.0d) / 2.0d;
            double recencyWeight = recencyWeight(signal.getLastInteractionTime());
            double genreMatch = Objects.equals(
                    normalizeKey(candidateItem.getGenreCode()),
                    normalizeKey(historyItem.getGenreCode())
            ) ? 1.0d : 0.0d;
            double artistMatch = Objects.equals(
                    normalizeKey(candidateItem.getArtistName()),
                    normalizeKey(historyItem.getArtistName())
            ) ? 1.0d : 0.0d;
            double rawAttention = (1.7d * similarity)
                    + (0.9d * weight)
                    + (0.6d * recencyWeight)
                    + (0.25d * genreMatch)
                    + (0.15d * artistMatch);

            contributions.add(new AttentionContribution(historyItem, historyEmbedding, weight, similarity, rawAttention));
        }

        double attentionScore = 0.5d;
        double collaborativeScore = 0.0d;
        Item anchorItem = null;
        HistorySignal anchorSignal = null;
        if (!contributions.isEmpty()) {
            double[] weights = softmax(contributions.stream().mapToDouble(AttentionContribution::rawAttention).toArray());
            double[] contextVector = new double[EMBEDDING_DIM];
            double bestSignal = Double.NEGATIVE_INFINITY;
            for (int index = 0; index < contributions.size(); index++) {
                AttentionContribution contribution = contributions.get(index);
                double attentionWeight = weights[index];
                addScaled(contextVector, contribution.embedding(), attentionWeight * contribution.preferenceWeight());
                collaborativeScore += attentionWeight * contribution.similarity();
                double signalScore = attentionWeight * contribution.preferenceWeight();
                if (signalScore > bestSignal) {
                    bestSignal = signalScore;
                    anchorItem = contribution.item();
                    anchorSignal = historySignals.get(contribution.item().getItemId());
                }
            }
            normalizeInPlace(contextVector);
            attentionScore = sigmoid(2.6d * cosine(candidateEmbedding, contextVector));
        }

        SocialSignal socialSignal = socialSignals.get(candidateItem.getItemId());
        double contentFeatureScore = featureScore(candidateItem, genrePreference, languagePreference, artistPreference);
        double socialFeatureScore = socialScore(
                candidateItem,
                socialSignal,
                socialGenrePreference,
                socialLanguagePreference,
                socialArtistPreference
        );
        FeedbackMatch feedbackMatch = matchFeedback(candidateItem, feedbackProfile);
        HistorySignal existingSignal = historySignals.get(candidateItem.getItemId());
        double seenPenalty = existingSignal == null
                ? 0.0d
                : 0.12d * Math.min(1.0d, existingSignal.getInteractionCount() / 4.0d);

        double finalScore = clamp(
                (0.31d * latentScore)
                        + (0.21d * attentionScore)
                        + (0.15d * collaborativeScore)
                        + (0.09d * contentFeatureScore)
                        + (0.12d * socialFeatureScore)
                        + (0.12d * feedbackMatch.totalScore())
                        - seenPenalty,
                0.0d,
                1.0d
        );

        String reasonText = buildReasonText(
                candidateItem,
                anchorItem,
                anchorSignal,
                socialSignal,
                genrePreference,
                languagePreference,
                artistPreference,
                socialGenrePreference,
                socialArtistPreference,
                feedbackMatch,
                contentFeatureScore,
                socialFeatureScore,
                collaborativeScore
        );

        return new CandidateScore(
                candidateItem.getItemId(),
                candidateItem.getTitle(),
                candidateItem.getArtistName(),
                candidateItem.getGenreCode(),
                finalScore,
                reasonText
        );
    }

    private void persistRecommendations(Long userId, List<RecommendationView> recommendations, String modelVersion) {
        recommendationFeatureMapper.deleteRecommendationsByUserIdFromTime(userId, dayStart());
        if (recommendations.isEmpty()) {
            return;
        }

        LocalDateTime requestTime = LocalDateTime.now(APP_ZONE_ID);
        String requestId = "ncf_attn_" + userId + "_" + requestTime
                .truncatedTo(ChronoUnit.SECONDS)
                .toString()
                .replace(":", "")
                .replace("-", "");

        List<RecommendationSnapshotRow> rows = new ArrayList<>();
        for (RecommendationView recommendation : recommendations) {
            RecommendationSnapshotRow row = new RecommendationSnapshotRow();
            row.setRequestId(requestId);
            row.setUserId(userId);
            row.setItemId(recommendation.getItemId());
            row.setModelVersion(modelVersion);
            row.setScore(roundScore(recommendation.getScore()));
            row.setRankNo(recommendation.getRankNo());
            row.setReasonText(recommendation.getReasonText());
            row.setScene(properties.getScene());
            row.setRequestTime(requestTime);
            rows.add(row);
        }
        recommendationFeatureMapper.insertRecommendationSnapshots(rows);
    }

    private LocalDateTime dayStart() {
        return LocalDate.now(APP_ZONE_ID).atStartOfDay();
    }

    private void ensureModelRegistry(String modelVersion) {
        recommendationFeatureMapper.upsertModelRegistry(
                modelVersion,
                properties.getModelName(),
                "NCF+Attention+Social",
                properties.getFeatureVersion(),
                properties.getSamplingVersion(),
                LocalDate.now(APP_ZONE_ID).toString(),
                "{\"scoreRange\":\"0-1\",\"mode\":\"online_ncf_attention_social\",\"topK\":10}",
                "memory://recommendation/" + modelVersion,
                "ready"
        );
    }

    private Map<Long, double[]> toUserEmbeddingMap(List<UserEmbeddingRow> rows) {
        Map<Long, double[]> vectors = new LinkedHashMap<>();
        for (UserEmbeddingRow row : rows) {
            vectors.put(row.getUserId(), parseVector(row.getEmbeddingVector()));
        }
        return vectors;
    }

    private Map<Long, double[]> toItemEmbeddingMap(List<ItemEmbeddingRow> rows) {
        Map<Long, double[]> vectors = new LinkedHashMap<>();
        for (ItemEmbeddingRow row : rows) {
            vectors.put(row.getItemId(), parseVector(row.getEmbeddingVector()));
        }
        return vectors;
    }

    private void accumulatePreferences(Collection<HistorySignal> signals,
                                       Map<Long, Item> itemById,
                                       Map<String, Double> genrePreference,
                                       Map<String, Double> languagePreference,
                                       Map<String, Double> artistPreference) {
        double totalWeight = 0.0d;
        for (HistorySignal signal : signals) {
            double weight = preferenceWeight(signal);
            if (weight <= 0.0d) {
                continue;
            }
            Item item = itemById.get(signal.getItemId());
            if (item == null) {
                continue;
            }
            totalWeight += weight;
            accumulate(genrePreference, item.getGenreCode(), weight);
            accumulate(languagePreference, item.getLanguageCode(), weight);
            accumulate(artistPreference, item.getArtistName(), weight);
        }

        if (totalWeight <= EPSILON) {
            return;
        }

        normalizePreferenceMap(genrePreference, totalWeight);
        normalizePreferenceMap(languagePreference, totalWeight);
        normalizePreferenceMap(artistPreference, totalWeight);
    }

    private void accumulateSocialPreferences(Collection<SocialSignal> signals,
                                             Map<Long, Item> itemById,
                                             Map<String, Double> genrePreference,
                                             Map<String, Double> languagePreference,
                                             Map<String, Double> artistPreference) {
        double totalWeight = 0.0d;
        for (SocialSignal signal : signals) {
            double weight = socialPreferenceWeight(signal);
            if (weight <= 0.0d) {
                continue;
            }
            Item item = itemById.get(signal.getItemId());
            if (item == null) {
                continue;
            }
            totalWeight += weight;
            accumulate(genrePreference, item.getGenreCode(), weight);
            accumulate(languagePreference, item.getLanguageCode(), weight);
            accumulate(artistPreference, item.getArtistName(), weight);
        }

        if (totalWeight <= EPSILON) {
            return;
        }

        normalizePreferenceMap(genrePreference, totalWeight);
        normalizePreferenceMap(languagePreference, totalWeight);
        normalizePreferenceMap(artistPreference, totalWeight);
    }

    private String buildReasonText(Item candidateItem,
                                   Item anchorItem,
                                   HistorySignal anchorSignal,
                                   SocialSignal socialSignal,
                                   Map<String, Double> genrePreference,
                                   Map<String, Double> languagePreference,
                                   Map<String, Double> artistPreference,
                                   Map<String, Double> socialGenrePreference,
                                   Map<String, Double> socialArtistPreference,
                                   FeedbackMatch feedbackMatch,
                                   double featureScore,
                                   double socialScore,
                                   double collaborativeScore) {
        double artistAffinity = artistPreference.getOrDefault(normalizeKey(candidateItem.getArtistName()), 0.0d);
        double genreAffinity = genrePreference.getOrDefault(normalizeKey(candidateItem.getGenreCode()), 0.0d);
        double languageAffinity = languagePreference.getOrDefault(normalizeKey(candidateItem.getLanguageCode()), 0.0d);
        double socialArtistAffinity = socialArtistPreference.getOrDefault(normalizeKey(candidateItem.getArtistName()), 0.0d);
        double socialGenreAffinity = socialGenrePreference.getOrDefault(normalizeKey(candidateItem.getGenreCode()), 0.0d);
        boolean sameArtistAsAnchor = anchorItem != null
                && normalizeKey(candidateItem.getArtistName()).equals(normalizeKey(anchorItem.getArtistName()));

        if (feedbackMatch.prefer() && feedbackMatch.magnitude() >= 0.18d) {
            return trimText(
                    "你在反馈工单中明确希望增加"
                            + feedbackTargetLabel(feedbackMatch.dimension(), feedbackMatch.value())
                            + "相关内容，因此这首歌获得了额外加权。",
                    255
            );
        }

        if (!feedbackMatch.prefer()
                && feedbackMatch.magnitude() >= 0.30d
                && (socialScore >= 0.40d || collaborativeScore >= 0.55d || featureScore >= 0.52d)) {
            return trimText(
                    "你曾在反馈工单中希望减少"
                            + feedbackTargetLabel(feedbackMatch.dimension(), feedbackMatch.value())
                            + "相关内容，但这首歌仍因近期历史、收藏或好友信号较强而保留在推荐中。",
                    255
            );
        }

        if (socialSignal != null && socialScore >= 0.42d) {
            String friendLabel = socialSignal.getTopFolloweeUsername() == null || socialSignal.getTopFolloweeUsername().isBlank()
                    ? "你关注的好友"
                    : "你关注的好友 " + socialSignal.getTopFolloweeUsername();
            if (socialSignal.getFavoriteCount() > 0) {
                return trimText(friendLabel + " 收藏或常听过这首歌，你们的社交偏好高度重合", 255);
            }
            if (socialArtistAffinity >= 0.18d) {
                return trimText(friendLabel + " 最近常听 " + candidateItem.getArtistName() + "，这首歌与你的好友圈偏好接近", 255);
            }
            if (socialGenreAffinity >= 0.20d) {
                return trimText(friendLabel + " 最近更偏好 " + genreLabel(candidateItem.getGenreCode()) + " 风格，这首歌受到社交信号加成", 255);
            }
            return trimText(friendLabel + " 的收藏和听歌历史与这首歌相似度较高", 255);
        }

        if (sameArtistAsAnchor || artistAffinity >= 0.18d) {
            String anchorHint = anchorItem == null ? "" : "，关联歌曲是" + quoteTitle(anchorItem.getTitle());
            return trimText("你对 " + candidateItem.getArtistName() + " 的同歌手偏好明显" + anchorHint, 255);
        }

        if (anchorItem != null && collaborativeScore >= 0.58d) {
            return trimText(
                    "这首歌与你" + describeAnchorSignal(anchorSignal) + quoteTitle(anchorItem.getTitle()) + "的风格更接近",
                    255
            );
        }

        if (genreAffinity >= 0.22d && languageAffinity >= 0.22d) {
            return trimText(
                    "符合你最近的 " + languageLabel(candidateItem.getLanguageCode()) + genreLabel(candidateItem.getGenreCode()) + " 听歌习惯",
                    255
            );
        }

        if (genreAffinity >= 0.30d) {
            return trimText("你最近更常听 " + genreLabel(candidateItem.getGenreCode()) + " 风格的歌曲", 255);
        }

        if (languageAffinity >= 0.30d) {
            return trimText("这首歌更贴合你最近的 " + languageLabel(candidateItem.getLanguageCode()) + " 听歌偏好", 255);
        }

        if (featureScore >= 0.55d) {
            return trimText("这首歌与您近期偏好的歌手和风格较为一致", 255);
        }

        return trimText("模型判断这首歌与你最近的听歌历史匹配度较高", 255);
    }

    private double[] buildUserEmbedding(User user,
                                        double[] sourceEmbedding,
                                        Collection<HistorySignal> signals,
                                        Collection<SocialSignal> socialSignals,
                                        FeedbackProfile feedbackProfile,
                                        Map<Long, double[]> itemEmbeddings) {
        double[] profileVector = normalize(weightedBlend(List.of(
                new WeightedVector(tokenVector("gender:" + user.getGender()), 0.25d),
                new WeightedVector(tokenVector("age:" + ageBucket(user.getBirthYear())), 0.25d),
                new WeightedVector(tokenVector("uid:" + user.getUserId()), 0.50d)
        )));

        List<WeightedVector> historyVectors = new ArrayList<>();
        for (HistorySignal signal : signals) {
            double[] itemEmbedding = itemEmbeddings.get(signal.getItemId());
            double weight = preferenceWeight(signal);
            if (itemEmbedding == null || weight <= 0.0d) {
                continue;
            }
            historyVectors.add(new WeightedVector(itemEmbedding, weight));
        }

        List<WeightedVector> socialVectors = new ArrayList<>();
        for (SocialSignal signal : socialSignals) {
            double[] itemEmbedding = itemEmbeddings.get(signal.getItemId());
            double weight = socialPreferenceWeight(signal);
            if (itemEmbedding == null || weight <= 0.0d) {
                continue;
            }
            socialVectors.add(new WeightedVector(itemEmbedding, weight));
        }

        double[] historyVector = historyVectors.isEmpty() ? null : normalize(weightedBlend(historyVectors));
        double[] socialVector = socialVectors.isEmpty() ? null : normalize(weightedBlend(socialVectors));
        double[] normalizedSource = sourceEmbedding == null ? null : normalize(sourceEmbedding);
        double[] feedbackVector = buildFeedbackVector(feedbackProfile);

        List<WeightedVector> blend = new ArrayList<>();
        addWeightedVector(blend, normalizedSource, 0.40d);
        addWeightedVector(blend, historyVector, 0.34d);
        addWeightedVector(blend, socialVector, 0.16d);
        addWeightedVector(blend, feedbackVector, 0.18d);
        addWeightedVector(blend, profileVector, 0.12d);
        return blendNormalizedVectors(blend, profileVector);
    }

    private double[] buildItemEmbedding(Item item, double[] sourceEmbedding) {
        double[] metadataVector = normalize(weightedBlend(List.of(
                new WeightedVector(tokenVector("artist:" + normalizeKey(item.getArtistName())), 0.38d),
                new WeightedVector(tokenVector("genre:" + normalizeKey(item.getGenreCode())), 0.22d),
                new WeightedVector(tokenVector("language:" + normalizeKey(item.getLanguageCode())), 0.14d),
                new WeightedVector(tokenVector("album:" + normalizeKey(item.getAlbumName())), 0.12d),
                new WeightedVector(tokenVector("title:" + normalizeKey(item.getTitle())), 0.14d)
        )));
        if (sourceEmbedding == null) {
            return metadataVector;
        }
        return normalize(weightedBlend(List.of(
                new WeightedVector(normalize(sourceEmbedding), 0.68d),
                new WeightedVector(metadataVector, 0.32d)
        )));
    }

    private double featureScore(Item candidateItem,
                                Map<String, Double> genrePreference,
                                Map<String, Double> languagePreference,
                                Map<String, Double> artistPreference) {
        double genreScore = genrePreference.getOrDefault(normalizeKey(candidateItem.getGenreCode()), 0.0d);
        double languageScore = languagePreference.getOrDefault(normalizeKey(candidateItem.getLanguageCode()), 0.0d);
        double artistScore = artistPreference.getOrDefault(normalizeKey(candidateItem.getArtistName()), 0.0d);
        return clamp(
                (0.50d * genreScore)
                        + (0.25d * languageScore)
                        + (0.25d * artistScore),
                0.0d,
                1.0d
        );
    }

    private FeedbackMatch matchFeedback(Item candidateItem, FeedbackProfile feedbackProfile) {
        if (feedbackProfile == null || feedbackProfile.isEmpty()) {
            return FeedbackMatch.none();
        }

        String genreKey = normalizeKey(candidateItem.getGenreCode());
        String artistKey = normalizeKey(candidateItem.getArtistName());
        String languageKey = normalizeKey(candidateItem.getLanguageCode());

        double preferGenre = feedbackProfile.preferGenreWeight(genreKey);
        double preferArtist = feedbackProfile.preferArtistWeight(artistKey);
        double preferLanguage = feedbackProfile.preferLanguageWeight(languageKey);
        double avoidGenre = feedbackProfile.avoidGenreWeight(genreKey);
        double avoidArtist = feedbackProfile.avoidArtistWeight(artistKey);
        double avoidLanguage = feedbackProfile.avoidLanguageWeight(languageKey);

        double positiveScore = clamp(
                (0.56d * preferGenre)
                        + (0.28d * preferArtist)
                        + (0.16d * preferLanguage),
                0.0d,
                1.0d
        );
        double negativeScore = clamp(
                (0.56d * avoidGenre)
                        + (0.28d * avoidArtist)
                        + (0.16d * avoidLanguage),
                0.0d,
                1.0d
        );

        FeedbackFocus focus = FeedbackFocus.none();
        focus = pickFeedbackFocus(focus, "GENRE", candidateItem.getGenreCode(), true, 0.56d * preferGenre);
        focus = pickFeedbackFocus(focus, "ARTIST", candidateItem.getArtistName(), true, 0.28d * preferArtist);
        focus = pickFeedbackFocus(focus, "LANGUAGE", candidateItem.getLanguageCode(), true, 0.16d * preferLanguage);
        focus = pickFeedbackFocus(focus, "GENRE", candidateItem.getGenreCode(), false, 0.56d * avoidGenre);
        focus = pickFeedbackFocus(focus, "ARTIST", candidateItem.getArtistName(), false, 0.28d * avoidArtist);
        focus = pickFeedbackFocus(focus, "LANGUAGE", candidateItem.getLanguageCode(), false, 0.16d * avoidLanguage);

        return new FeedbackMatch(
                positiveScore,
                negativeScore,
                clamp(positiveScore - negativeScore, -1.0d, 1.0d),
                focus.dimension(),
                focus.value(),
                focus.prefer(),
                focus.magnitude()
        );
    }

    private double socialScore(Item candidateItem,
                               SocialSignal socialSignal,
                               Map<String, Double> socialGenrePreference,
                               Map<String, Double> socialLanguagePreference,
                               Map<String, Double> socialArtistPreference) {
        double itemAffinity = socialSignal == null ? 0.0d : socialPreferenceWeight(socialSignal);
        double genreAffinity = socialGenrePreference.getOrDefault(normalizeKey(candidateItem.getGenreCode()), 0.0d);
        double languageAffinity = socialLanguagePreference.getOrDefault(normalizeKey(candidateItem.getLanguageCode()), 0.0d);
        double artistAffinity = socialArtistPreference.getOrDefault(normalizeKey(candidateItem.getArtistName()), 0.0d);
        return clamp(
                (0.46d * itemAffinity)
                        + (0.26d * artistAffinity)
                        + (0.18d * genreAffinity)
                        + (0.10d * languageAffinity),
                0.0d,
                1.0d
        );
    }

    private FeedbackFocus pickFeedbackFocus(FeedbackFocus current,
                                            String dimension,
                                            String value,
                                            boolean prefer,
                                            double magnitude) {
        if (magnitude <= current.magnitude()) {
            return current;
        }
        return new FeedbackFocus(dimension, value, prefer, magnitude);
    }

    private double preferenceWeight(HistorySignal signal) {
        double interactionScore = Math.min(1.0d, signal.getInteractionCount() / 4.0d);
        double completionScore = Math.min(1.0d, signal.getCompletePlayCount() / 3.0d);
        double favoriteScore = signal.isFavorite() ? 1.0d : 0.0d;
        double dislikePenalty = signal.isDislike() ? 1.0d : 0.0d;
        double ratingScore = signal.getExplicitRating() <= 0.0d
                ? 0.0d
                : Math.max(0.0d, (signal.getExplicitRating() - 1.0d) / 4.0d);
        double recencyScore = recencyWeight(signal.getLastInteractionTime());
        return clamp(
                (0.28d * interactionScore)
                        + (0.24d * completionScore)
                        + (0.22d * favoriteScore)
                        + (0.16d * ratingScore)
                        + (0.10d * recencyScore)
                        - (0.40d * dislikePenalty),
                0.0d,
                1.0d
        );
    }

    private double friendPreferenceWeight(HistorySignal signal) {
        double directWeight = preferenceWeight(signal);
        double favoriteBoost = signal.isFavorite() ? 0.10d : 0.0d;
        return clamp((directWeight * 0.72d) + favoriteBoost, 0.0d, 1.0d);
    }

    private double socialPreferenceWeight(SocialSignal signal) {
        double followeeScore = Math.min(1.0d, signal.getFolloweeCount() / 2.0d);
        double favoriteScore = Math.min(1.0d, signal.getFavoriteCount() / 2.0d);
        double interactionScore = Math.min(1.0d, signal.getInteractionCount() / 5.0d);
        double completionScore = Math.min(1.0d, signal.getCompletePlayCount() / 4.0d);
        double recencyScore = recencyWeight(signal.getLastInteractionTime());
        return clamp(
                (0.28d * followeeScore)
                        + (0.26d * favoriteScore)
                        + (0.18d * interactionScore)
                        + (0.18d * completionScore)
                        + (0.10d * recencyScore),
                0.0d,
                1.0d
        );
    }

    private double recencyWeight(LocalDateTime lastInteractionTime) {
        if (lastInteractionTime == null) {
            return 0.35d;
        }
        long days = Math.max(0L, ChronoUnit.DAYS.between(lastInteractionTime.toLocalDate(), LocalDate.now(APP_ZONE_ID)));
        return Math.exp(-days / 21.0d);
    }

    private void accumulate(Map<String, Double> container, String key, double weight) {
        if (key == null || key.isBlank()) {
            return;
        }
        container.merge(normalizeKey(key), weight, Double::sum);
    }

    private void normalizePreferenceMap(Map<String, Double> preferenceMap, double totalWeight) {
        for (Map.Entry<String, Double> entry : preferenceMap.entrySet()) {
            entry.setValue(entry.getValue() / totalWeight);
        }
    }

    private void addWeightedVector(List<WeightedVector> vectors, double[] vector, double weight) {
        if (vector == null || weight <= 0.0d) {
            return;
        }
        vectors.add(new WeightedVector(vector, weight));
    }

    private double[] blendNormalizedVectors(List<WeightedVector> vectors, double[] fallback) {
        double[] combined = new double[EMBEDDING_DIM];
        double totalWeight = 0.0d;
        for (WeightedVector vector : vectors) {
            if (vector == null || vector.vector() == null || vector.weight() <= 0.0d) {
                continue;
            }
            addScaled(combined, vector.vector(), vector.weight());
            totalWeight += vector.weight();
        }
        if (totalWeight <= EPSILON) {
            return fallback == null ? new double[EMBEDDING_DIM] : fallback;
        }
        for (int index = 0; index < combined.length; index++) {
            combined[index] /= totalWeight;
        }
        return normalize(combined);
    }

    private double[] buildFeedbackVector(FeedbackProfile feedbackProfile) {
        if (feedbackProfile == null || feedbackProfile.isEmpty()) {
            return null;
        }
        double[] vector = new double[EMBEDDING_DIM];
        feedbackProfile.preferGenre.forEach((key, weight) ->
                addScaled(vector, tokenVector("feedback:prefer:genre:" + key), 0.58d * weight));
        feedbackProfile.preferArtist.forEach((key, weight) ->
                addScaled(vector, tokenVector("feedback:prefer:artist:" + key), 0.28d * weight));
        feedbackProfile.preferLanguage.forEach((key, weight) ->
                addScaled(vector, tokenVector("feedback:prefer:language:" + key), 0.14d * weight));
        feedbackProfile.avoidGenre.forEach((key, weight) ->
                addScaled(vector, tokenVector("feedback:avoid:genre:" + key), -0.62d * weight));
        feedbackProfile.avoidArtist.forEach((key, weight) ->
                addScaled(vector, tokenVector("feedback:avoid:artist:" + key), -0.24d * weight));
        feedbackProfile.avoidLanguage.forEach((key, weight) ->
                addScaled(vector, tokenVector("feedback:avoid:language:" + key), -0.14d * weight));
        return norm(vector) <= EPSILON ? null : normalize(vector);
    }

    private String ageBucket(Integer birthYear) {
        if (birthYear == null || birthYear <= 0) {
            return "unknown";
        }
        int age = Math.max(0, LocalDate.now(APP_ZONE_ID).getYear() - birthYear);
        if (age < 20) {
            return "teen";
        }
        if (age < 28) {
            return "20s";
        }
        if (age < 38) {
            return "30s";
        }
        return "40plus";
    }

    private double[] tokenVector(String token) {
        long seed = 1469598103934665603L;
        String normalized = token == null ? "null" : token;
        for (int index = 0; index < normalized.length(); index++) {
            seed ^= normalized.charAt(index);
            seed *= 1099511628211L;
        }

        double[] vector = new double[EMBEDDING_DIM];
        long state = seed;
        for (int index = 0; index < EMBEDDING_DIM; index++) {
            state = mix64(state + index * 0x9E3779B97F4A7C15L);
            double normalizedValue = ((state >>> 11) & 0x1FFFFFL) / (double) 0x1FFFFF;
            vector[index] = (normalizedValue * 2.0d) - 1.0d;
        }
        return normalize(vector);
    }

    private long mix64(long value) {
        value ^= (value >>> 33);
        value *= 0xff51afd7ed558ccdL;
        value ^= (value >>> 33);
        value *= 0xc4ceb9fe1a85ec53L;
        value ^= (value >>> 33);
        return value;
    }

    private double[] parseVector(String json) {
        if (json == null || json.isBlank()) {
            return new double[EMBEDDING_DIM];
        }
        try {
            double[] vector = objectMapper.readValue(json, double[].class);
            if (vector.length == EMBEDDING_DIM) {
                return normalize(vector);
            }
            double[] resized = new double[EMBEDDING_DIM];
            for (int index = 0; index < Math.min(vector.length, EMBEDDING_DIM); index++) {
                resized[index] = vector[index];
            }
            return normalize(resized);
        } catch (JsonProcessingException ex) {
            log.warn("Skip invalid embedding vector: {}", ex.getMessage());
            return new double[EMBEDDING_DIM];
        }
    }

    private String toJson(double[] vector) {
        try {
            return objectMapper.writeValueAsString(vector);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize embedding vector", ex);
        }
    }

    private double[] weightedBlend(List<WeightedVector> vectors) {
        double[] combined = new double[EMBEDDING_DIM];
        for (WeightedVector vector : vectors) {
            if (vector == null || vector.vector() == null || vector.weight() <= 0.0d) {
                continue;
            }
            addScaled(combined, vector.vector(), vector.weight());
        }
        return combined;
    }

    private void addScaled(double[] target, double[] source, double weight) {
        for (int index = 0; index < Math.min(target.length, source.length); index++) {
            target[index] += source[index] * weight;
        }
    }

    private double[] normalize(double[] vector) {
        double[] copied = vector.clone();
        normalizeInPlace(copied);
        return copied;
    }

    private void normalizeInPlace(double[] vector) {
        double norm = 0.0d;
        for (double value : vector) {
            norm += value * value;
        }
        norm = Math.sqrt(norm);
        if (norm <= EPSILON) {
            return;
        }
        for (int index = 0; index < vector.length; index++) {
            vector[index] /= norm;
        }
    }

    private double cosine(double[] left, double[] right) {
        if (left == null || right == null) {
            return 0.0d;
        }
        double numerator = 0.0d;
        double leftNorm = 0.0d;
        double rightNorm = 0.0d;
        for (int index = 0; index < Math.min(left.length, right.length); index++) {
            numerator += left[index] * right[index];
            leftNorm += left[index] * left[index];
            rightNorm += right[index] * right[index];
        }
        if (leftNorm <= EPSILON || rightNorm <= EPSILON) {
            return 0.0d;
        }
        return numerator / Math.sqrt(leftNorm * rightNorm);
    }

    private double norm(double[] vector) {
        double norm = 0.0d;
        for (double value : vector) {
            norm += value * value;
        }
        return Math.sqrt(norm);
    }

    private double[] softmax(double[] rawScores) {
        double max = Double.NEGATIVE_INFINITY;
        for (double rawScore : rawScores) {
            max = Math.max(max, rawScore);
        }
        double sum = 0.0d;
        double[] weights = new double[rawScores.length];
        for (int index = 0; index < rawScores.length; index++) {
            weights[index] = Math.exp(rawScores[index] - max);
            sum += weights[index];
        }
        if (sum <= EPSILON) {
            return weights;
        }
        for (int index = 0; index < weights.length; index++) {
            weights[index] /= sum;
        }
        return weights;
    }

    private double sigmoid(double value) {
        return 1.0d / (1.0d + Math.exp(-value));
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private String normalizeKey(Object value) {
        return value == null ? "" : value.toString().trim().toLowerCase(Locale.ROOT);
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : Math.max(0, value);
    }

    private LocalDateTime latestOf(LocalDateTime left, LocalDateTime right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return left.isAfter(right) ? left : right;
    }

    private double roundScore(Double score) {
        if (score == null) {
            return 0.0d;
        }
        return Math.round(score * 1_000_000d) / 1_000_000d;
    }

    private String trimText(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, Math.max(0, maxLength - 3)) + "...";
    }

    private String feedbackTargetLabel(String dimension, String value) {
        String safeValue = value == null || value.isBlank() ? "该类" : value;
        return switch (normalizeKey(dimension).toUpperCase(Locale.ROOT)) {
            case "GENRE" -> genreLabel(safeValue);
            case "ARTIST" -> safeValue + " 的歌曲";
            case "LANGUAGE" -> languageLabel(safeValue) + "歌曲";
            default -> safeValue;
        };
    }

    private String quoteTitle(String title) {
        return "《" + trimText(title == null ? "这首歌" : title, 28) + "》";
    }

    private String describeAnchorSignal(HistorySignal anchorSignal) {
        if (anchorSignal == null) {
            return "最近高互动歌曲 ";
        }
        if (anchorSignal.isFavorite()) {
            return "收藏过的歌曲 ";
        }
        if (anchorSignal.getExplicitRating() >= 4.0d) {
            return "高评分歌曲 ";
        }
        if (anchorSignal.getCompletePlayCount() > 0) {
            return "最近完整听过的歌曲 ";
        }
        if (anchorSignal.getInteractionCount() >= 3) {
            return "最近经常播放的歌曲 ";
        }
        return "最近高互动歌曲 ";
    }

    private String genreLabel(String genreCode) {
        return switch (normalizeKey(genreCode).toUpperCase(Locale.ROOT)) {
            case "POP" -> "流行";
            case "ROCK" -> "摇滚";
            case "FOLK" -> "民谣";
            case "EDM" -> "电子";
            case "MANDOPOP" -> "华语流行";
            default -> genreCode == null || genreCode.isBlank() ? "音乐" : genreCode;
        };
    }

    private String languageLabel(String languageCode) {
        return switch (normalizeKey(languageCode)) {
            case "zh" -> "中文";
            case "en" -> "英文";
            case "jp" -> "日文";
            case "kr" -> "韩文";
            default -> languageCode == null || languageCode.isBlank() ? "多语种" : languageCode.toUpperCase(Locale.ROOT);
        };
    }

    private record RecommendationDataset(
            String modelVersion,
            Map<Long, User> users,
            Map<Long, Item> items,
            Map<Long, double[]> itemEmbeddings,
            Map<Long, double[]> userEmbeddings,
            Map<Long, Map<Long, HistorySignal>> historyByUser,
            Map<Long, Map<Long, SocialSignal>> socialSignalsByUser,
            Map<Long, FeedbackProfile> feedbackProfiles
    ) {
    }

    private record WeightedVector(double[] vector, double weight) {
    }

    private record AttentionContribution(Item item,
                                         double[] embedding,
                                         double preferenceWeight,
                                         double similarity,
                                         double rawAttention) {
    }

    private record CandidateScore(Long itemId,
                                  String title,
                                  String artistName,
                                  String genreCode,
                                  double finalScore,
                                  String reasonText) {
        private static CandidateScore empty(Item item) {
            return new CandidateScore(
                    item.getItemId(),
                    item.getTitle(),
                    item.getArtistName(),
                    item.getGenreCode(),
                    0.0d,
                    ""
            );
        }
    }

    private record FeedbackMatch(double positiveScore,
                                 double negativeScore,
                                 double totalScore,
                                 String dimension,
                                 String value,
                                 boolean prefer,
                                 double magnitude) {
        private static FeedbackMatch none() {
            return new FeedbackMatch(0.0d, 0.0d, 0.0d, "", "", true, 0.0d);
        }
    }

    private record FeedbackFocus(String dimension, String value, boolean prefer, double magnitude) {
        private static FeedbackFocus none() {
            return new FeedbackFocus("", "", true, 0.0d);
        }
    }

    private static final class HistorySignal {
        private final Long itemId;
        private int interactionCount;
        private int completePlayCount;
        private boolean favorite;
        private boolean dislike;
        private double explicitRating;
        private LocalDateTime lastInteractionTime;

        private HistorySignal(Long itemId) {
            this.itemId = itemId;
        }

        public Long getItemId() {
            return itemId;
        }

        public int getInteractionCount() {
            return interactionCount;
        }

        public void setInteractionCount(int interactionCount) {
            this.interactionCount = Math.max(this.interactionCount, interactionCount);
        }

        public int getCompletePlayCount() {
            return completePlayCount;
        }

        public void setCompletePlayCount(int completePlayCount) {
            this.completePlayCount = Math.max(this.completePlayCount, completePlayCount);
        }

        public boolean isFavorite() {
            return favorite;
        }

        public void setFavorite(boolean favorite) {
            this.favorite = favorite;
        }

        public boolean isDislike() {
            return dislike;
        }

        public void setDislike(boolean dislike) {
            this.dislike = dislike;
        }

        public double getExplicitRating() {
            return explicitRating;
        }

        public void setExplicitRating(double explicitRating) {
            this.explicitRating = Math.max(this.explicitRating, explicitRating);
        }

        public LocalDateTime getLastInteractionTime() {
            return lastInteractionTime;
        }

        public void setLastInteractionTime(LocalDateTime lastInteractionTime) {
            this.lastInteractionTime = lastInteractionTime;
        }
    }

    private static final class FeedbackProfile {
        private final Map<String, Double> preferGenre = new LinkedHashMap<>();
        private final Map<String, Double> preferArtist = new LinkedHashMap<>();
        private final Map<String, Double> preferLanguage = new LinkedHashMap<>();
        private final Map<String, Double> avoidGenre = new LinkedHashMap<>();
        private final Map<String, Double> avoidArtist = new LinkedHashMap<>();
        private final Map<String, Double> avoidLanguage = new LinkedHashMap<>();

        private void absorb(UserFeedbackTicket ticket, double strength) {
            merge(ticket.getPreferDimension(), ticket.getPreferValue(), strength, true);
            merge(ticket.getAvoidDimension(), ticket.getAvoidValue(), strength, false);
        }

        private boolean isEmpty() {
            return preferGenre.isEmpty()
                    && preferArtist.isEmpty()
                    && preferLanguage.isEmpty()
                    && avoidGenre.isEmpty()
                    && avoidArtist.isEmpty()
                    && avoidLanguage.isEmpty();
        }

        private double preferGenreWeight(String key) {
            return preferGenre.getOrDefault(key, 0.0d);
        }

        private double preferArtistWeight(String key) {
            return preferArtist.getOrDefault(key, 0.0d);
        }

        private double preferLanguageWeight(String key) {
            return preferLanguage.getOrDefault(key, 0.0d);
        }

        private double avoidGenreWeight(String key) {
            return avoidGenre.getOrDefault(key, 0.0d);
        }

        private double avoidArtistWeight(String key) {
            return avoidArtist.getOrDefault(key, 0.0d);
        }

        private double avoidLanguageWeight(String key) {
            return avoidLanguage.getOrDefault(key, 0.0d);
        }

        private void merge(String dimension, String value, double strength, boolean prefer) {
            if (dimension == null || dimension.isBlank() || value == null || value.isBlank()) {
                return;
            }
            Map<String, Double> target = switch (dimension.trim().toUpperCase(Locale.ROOT)) {
                case "GENRE" -> prefer ? preferGenre : avoidGenre;
                case "ARTIST" -> prefer ? preferArtist : avoidArtist;
                case "LANGUAGE" -> prefer ? preferLanguage : avoidLanguage;
                default -> null;
            };
            if (target == null) {
                return;
            }
            String normalized = value.trim().toLowerCase(Locale.ROOT);
            target.merge(normalized, strength, Double::sum);
        }
    }

    private static final class SocialSignal {
        private final Long itemId;
        private int followeeCount;
        private int favoriteCount;
        private int interactionCount;
        private int completePlayCount;
        private double strongestWeight;
        private String topFolloweeUsername;
        private LocalDateTime lastInteractionTime;

        private SocialSignal(Long itemId) {
            this.itemId = itemId;
        }

        public Long getItemId() {
            return itemId;
        }

        public int getFolloweeCount() {
            return followeeCount;
        }

        public int getFavoriteCount() {
            return favoriteCount;
        }

        public int getInteractionCount() {
            return interactionCount;
        }

        public int getCompletePlayCount() {
            return completePlayCount;
        }

        public String getTopFolloweeUsername() {
            return topFolloweeUsername;
        }

        public LocalDateTime getLastInteractionTime() {
            return lastInteractionTime;
        }

        private void absorb(UserFollowEdge edge, HistorySignal signal, double weight) {
            followeeCount += 1;
            if (signal.isFavorite()) {
                favoriteCount += 1;
            }
            interactionCount += signal.getInteractionCount();
            completePlayCount += signal.getCompletePlayCount();
            lastInteractionTime = mergeTime(lastInteractionTime, signal.getLastInteractionTime());
            if (weight >= strongestWeight) {
                strongestWeight = weight;
                topFolloweeUsername = edge.getFolloweeUsername();
            }
        }

        private LocalDateTime mergeTime(LocalDateTime current, LocalDateTime candidate) {
            if (current == null) {
                return candidate;
            }
            if (candidate == null) {
                return current;
            }
            return current.isAfter(candidate) ? current : candidate;
        }
    }
}

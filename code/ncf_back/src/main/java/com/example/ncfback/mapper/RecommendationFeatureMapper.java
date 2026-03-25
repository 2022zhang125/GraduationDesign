package com.example.ncfback.mapper;

import com.example.ncfback.entity.ExplicitRatingRow;
import com.example.ncfback.entity.FavoriteEdge;
import com.example.ncfback.entity.Item;
import com.example.ncfback.entity.ItemEmbeddingRow;
import com.example.ncfback.entity.RecommendationSnapshotRow;
import com.example.ncfback.entity.RecommendationExposureRow;
import com.example.ncfback.entity.RecommendationView;
import com.example.ncfback.entity.User;
import com.example.ncfback.entity.UserEmbeddingRow;
import com.example.ncfback.entity.UserItemFeatureRow;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface RecommendationFeatureMapper {
    String findLatestReadyModelVersion();

    List<User> findAllActiveUsers();

    User findActiveUserById(@Param("userId") Long userId);

    List<Item> findAllActiveItems();

    List<UserEmbeddingRow> findUserEmbeddings(@Param("modelVersion") String modelVersion);

    List<ItemEmbeddingRow> findItemEmbeddings(@Param("modelVersion") String modelVersion);

    List<UserItemFeatureRow> findLatestUserItemFeatures();

    List<UserItemFeatureRow> findLatestUserItemFeaturesByUserId(@Param("userId") Long userId);

    List<ExplicitRatingRow> findExplicitRatings();

    List<ExplicitRatingRow> findExplicitRatingsByUserId(@Param("userId") Long userId);

    List<FavoriteEdge> findAllFavorites();

    List<FavoriteEdge> findFavoritesByUserId(@Param("userId") Long userId);

    UserEmbeddingRow findUserEmbeddingByUserId(@Param("modelVersion") String modelVersion,
                                               @Param("userId") Long userId);

    long countListeningInteractionsByUserId(@Param("userId") Long userId);

    long countExplicitRatingsByUserId(@Param("userId") Long userId);

    List<RecommendationView> findColdStartTopItems(@Param("limit") int limit);

    List<RecommendationExposureRow> findRecentRecommendationExposure(@Param("userId") Long userId,
                                                                    @Param("fromTime") java.time.LocalDateTime fromTime,
                                                                    @Param("toTime") java.time.LocalDateTime toTime);

    int upsertModelRegistry(@Param("modelVersion") String modelVersion,
                            @Param("modelName") String modelName,
                            @Param("algorithm") String algorithm,
                            @Param("featureVersion") String featureVersion,
                            @Param("samplingVersion") String samplingVersion,
                            @Param("trainSnapshotDate") String trainSnapshotDate,
                            @Param("metricsJson") String metricsJson,
                            @Param("modelUri") String modelUri,
                            @Param("status") String status);

    int upsertUserEmbeddings(@Param("records") List<UserEmbeddingRow> records);

    int upsertItemEmbeddings(@Param("records") List<ItemEmbeddingRow> records);

    int deleteRecommendationsByUserId(@Param("userId") Long userId);

    int deleteRecommendationsByUserIdFromTime(@Param("userId") Long userId,
                                              @Param("fromTime") java.time.LocalDateTime fromTime);

    int insertRecommendationSnapshots(@Param("records") List<RecommendationSnapshotRow> records);
}

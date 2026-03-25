package com.example.ncfback.mapper;

import com.example.ncfback.entity.ListeningHistoryView;
import com.example.ncfback.entity.ListeningInteractionRecord;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface ListeningHistoryMapper {
    List<ListeningHistoryView> findPage(@Param("userId") Long userId,
                                        @Param("offset") int offset,
                                        @Param("limit") int limit);

    long countHistory(@Param("userId") Long userId);

    LocalDate findCurrentFeatureDate();

    int insertInteraction(ListeningInteractionRecord record);

    int upsertUserItemFeature(@Param("featureDate") LocalDate featureDate,
                              @Param("userId") Long userId,
                              @Param("itemId") Long itemId,
                              @Param("lastInteractionTime") LocalDateTime lastInteractionTime,
                              @Param("interactionDelta") int interactionDelta,
                              @Param("completeDelta") int completeDelta,
                              @Param("favoriteFlagEver") int favoriteFlagEver,
                              @Param("dislikeFlag30d") int dislikeFlag30d);
}

package com.example.ncfback.service;

import com.example.ncfback.dto.ListeningEventRequest;
import com.example.ncfback.dto.PageResponse;
import com.example.ncfback.entity.ListeningHistoryView;
import com.example.ncfback.entity.ListeningInteractionRecord;
import com.example.ncfback.mapper.FavoriteMapper;
import com.example.ncfback.mapper.ListeningHistoryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ListeningHistoryService {

    private static final String EVENT_PLAY_START = "play_start";
    private static final String EVENT_PLAY_STOP = "play_stop";
    private static final String EVENT_PLAY_COMPLETE = "play_complete";
    private static final ZoneId APP_ZONE_ID = ZoneId.of("Asia/Shanghai");

    private final ListeningHistoryMapper listeningHistoryMapper;
    private final UserService userService;
    private final ItemService itemService;
    private final FavoriteMapper favoriteMapper;
    private final RecommendationService recommendationService;

    public PageResponse<ListeningHistoryView> pageByUserId(Long userId, int page, int size) {
        userService.getById(userId);
        return page(userId, page, size);
    }

    public PageResponse<ListeningHistoryView> pageByUserKey(String userKey, int page, int size) {
        return page(resolveUserId(userKey), page, size);
    }

    public PageResponse<ListeningHistoryView> page(Long userId, int page, int size) {
        if (userId != null) {
            userService.getById(userId);
        }
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 100);
        int offset = (safePage - 1) * safeSize;

        List<ListeningHistoryView> list = listeningHistoryMapper.findPage(userId, offset, safeSize);
        long total = listeningHistoryMapper.countHistory(userId);
        return new PageResponse<>(list, total, safePage, safeSize);
    }

    @Transactional
    public void recordPlayStart(Long userId, Long itemId, ListeningEventRequest request) {
        userService.getById(userId);
        itemService.getById(itemId);

        LocalDateTime now = now();
        insertInteraction(userId, itemId, EVENT_PLAY_START, now, null, null, request);
        upsertUserItemFeature(userId, itemId, now, 1, 0);
        recommendationService.refreshUserRecommendations(userId);
    }

    @Transactional
    public void recordPlayComplete(Long userId, Long itemId, ListeningEventRequest request) {
        userService.getById(userId);
        itemService.getById(itemId);

        Integer playedSeconds = sanitizePositiveInt(request == null ? null : request.getPlayedSeconds());
        Integer totalPlayedSeconds = sanitizePositiveInt(request == null ? null : request.getTotalPlayedSeconds());
        Integer durationSeconds = sanitizePositiveInt(request == null ? null : request.getDurationSeconds());
        Integer effectivePlayedSeconds = totalPlayedSeconds != null ? totalPlayedSeconds : playedSeconds;
        Double watchRatio = calculateWatchRatio(effectivePlayedSeconds, durationSeconds);
        String eventType = resolveSessionEndEventType(request, watchRatio);

        LocalDateTime now = now();
        insertInteraction(userId, itemId, eventType, now, playedSeconds, watchRatio, request);
        upsertUserItemFeature(userId, itemId, now, 0, EVENT_PLAY_COMPLETE.equals(eventType) ? 1 : 0);
    }

    private void insertInteraction(Long userId,
                                   Long itemId,
                                   String eventType,
                                   LocalDateTime eventTime,
                                   Integer playedSeconds,
                                   Double watchRatio,
                                   ListeningEventRequest request) {
        ListeningInteractionRecord record = new ListeningInteractionRecord();
        record.setRequestId(normalizeRequestId(request == null ? null : request.getRequestId()));
        record.setTraceId(UUID.randomUUID().toString().replace("-", ""));
        record.setUserId(userId);
        record.setItemId(itemId);
        record.setEventType(eventType);
        record.setEventTime(eventTime);
        record.setPlayDurationSec(playedSeconds);
        record.setWatchRatio(watchRatio);
        record.setDeviceType("web");
        record.setChannel(normalizeChannel(request == null ? null : request.getSource()));
        record.setPositionNo(null);
        record.setContextJson(null);
        listeningHistoryMapper.insertInteraction(record);
    }

    private void upsertUserItemFeature(Long userId,
                                       Long itemId,
                                       LocalDateTime lastInteractionTime,
                                       int interactionDelta,
                                       int completeDelta) {
        LocalDate featureDate = listeningHistoryMapper.findCurrentFeatureDate();
        int favoriteFlag = favoriteMapper.exists(userId, itemId) > 0 ? 1 : 0;
        listeningHistoryMapper.upsertUserItemFeature(
                featureDate,
                userId,
                itemId,
                lastInteractionTime,
                Math.max(interactionDelta, 0),
                Math.max(completeDelta, 0),
                favoriteFlag,
                0
        );
    }

    private String normalizeRequestId(String requestId) {
        if (requestId == null || requestId.isBlank()) {
            return "listen_" + UUID.randomUUID().toString().replace("-", "");
        }
        String normalized = requestId.trim();
        return normalized.length() > 64 ? normalized.substring(0, 64) : normalized;
    }

    private String normalizeChannel(String source) {
        if (source == null || source.isBlank()) {
            return "web_player";
        }
        String normalized = source.trim();
        return normalized.length() > 32 ? normalized.substring(0, 32) : normalized;
    }

    private Integer sanitizePositiveInt(Integer value) {
        if (value == null || value <= 0) {
            return null;
        }
        return value;
    }

    private String resolveSessionEndEventType(ListeningEventRequest request, Double watchRatio) {
        if (request != null && request.getCompleted() != null) {
            return Boolean.TRUE.equals(request.getCompleted()) ? EVENT_PLAY_COMPLETE : EVENT_PLAY_STOP;
        }
        if (watchRatio != null && watchRatio >= 0.95d) {
            return EVENT_PLAY_COMPLETE;
        }
        return EVENT_PLAY_STOP;
    }

    private Double calculateWatchRatio(Integer playedSeconds, Integer durationSeconds) {
        if (playedSeconds == null || durationSeconds == null || durationSeconds <= 0) {
            return null;
        }
        double ratio = Math.max(0.0d, Math.min(1.0d, playedSeconds.doubleValue() / durationSeconds.doubleValue()));
        return Math.round(ratio * 100000d) / 100000d;
    }

    private LocalDateTime now() {
        return LocalDateTime.now(APP_ZONE_ID);
    }

    private Long resolveUserId(String userKey) {
        if (userKey == null || userKey.isBlank()) {
            return null;
        }
        return userService.resolveUserId(userKey);
    }
}

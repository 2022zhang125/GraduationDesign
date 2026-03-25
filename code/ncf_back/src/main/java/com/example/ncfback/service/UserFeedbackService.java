package com.example.ncfback.service;

import com.example.ncfback.dto.FeedbackTicketCreateRequest;
import com.example.ncfback.dto.PageResponse;
import com.example.ncfback.entity.UserFeedbackTicket;
import com.example.ncfback.mapper.UserFeedbackMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class UserFeedbackService {

    private static final List<String> SUPPORTED_DIMENSIONS = List.of("GENRE", "ARTIST", "LANGUAGE");

    private final UserFeedbackMapper userFeedbackMapper;
    private final UserService userService;
    private final RecommendationService recommendationService;

    public PageResponse<UserFeedbackTicket> pageByUserId(Long userId, String status, int page, int size) {
        userService.getById(userId);
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 100);
        int offset = (safePage - 1) * safeSize;
        String normalizedStatus = normalizeStatus(status);

        List<UserFeedbackTicket> rows = userFeedbackMapper.findPageByUserId(userId, normalizedStatus, offset, safeSize);
        long total = userFeedbackMapper.countByUserId(userId, normalizedStatus);
        return new PageResponse<>(rows, total, safePage, safeSize);
    }

    @Transactional
    public UserFeedbackTicket createTicket(Long userId, FeedbackTicketCreateRequest request) {
        userService.getById(userId);
        UserFeedbackTicket ticket = new UserFeedbackTicket();
        ticket.setUserId(userId);
        ticket.setPreferDimension(normalizeDimension(request.getPreferDimension()));
        ticket.setPreferValue(normalizeValue(request.getPreferValue()));
        ticket.setAvoidDimension(normalizeDimension(request.getAvoidDimension()));
        ticket.setAvoidValue(normalizeValue(request.getAvoidValue()));
        ticket.setPreferenceStrength(normalizeStrength(request.getPreferenceStrength()));
        ticket.setDetailText(normalizeDetailText(request.getDetailText()));
        ticket.setStatus("ACTIVE");

        validateTicket(ticket);

        int affected = userFeedbackMapper.insert(ticket);
        if (affected <= 0 || ticket.getTicketId() == null) {
            throw new IllegalArgumentException("Create feedback ticket failed");
        }

        recommendationService.refreshUserRecommendations(userId);
        return userFeedbackMapper.findPageByUserId(userId, null, 0, 1).stream()
                .filter(row -> ticket.getTicketId().equals(row.getTicketId()))
                .findFirst()
                .orElse(ticket);
    }

    @Transactional
    public void closeTicket(Long userId, Long ticketId) {
        userService.getById(userId);
        int affected = userFeedbackMapper.closeTicket(ticketId, userId);
        if (affected <= 0) {
            throw new IllegalArgumentException("Feedback ticket not found");
        }
        recommendationService.refreshUserRecommendations(userId);
    }

    private void validateTicket(UserFeedbackTicket ticket) {
        boolean hasPrefer = StringUtils.hasText(ticket.getPreferDimension()) && StringUtils.hasText(ticket.getPreferValue());
        boolean hasAvoid = StringUtils.hasText(ticket.getAvoidDimension()) && StringUtils.hasText(ticket.getAvoidValue());
        if (!hasPrefer && !hasAvoid) {
            throw new IllegalArgumentException("At least one preferred or avoided music tendency is required");
        }
        if (StringUtils.hasText(ticket.getPreferDimension()) ^ StringUtils.hasText(ticket.getPreferValue())) {
            throw new IllegalArgumentException("Preferred music dimension and value must be provided together");
        }
        if (StringUtils.hasText(ticket.getAvoidDimension()) ^ StringUtils.hasText(ticket.getAvoidValue())) {
            throw new IllegalArgumentException("Avoided music dimension and value must be provided together");
        }
    }

    private String normalizeDimension(String dimension) {
        if (!StringUtils.hasText(dimension)) {
            return null;
        }
        String normalized = dimension.trim().toUpperCase(Locale.ROOT);
        if (!SUPPORTED_DIMENSIONS.contains(normalized)) {
            throw new IllegalArgumentException("Unsupported feedback dimension: " + dimension);
        }
        return normalized;
    }

    private String normalizeValue(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim();
        return normalized.length() > 128 ? normalized.substring(0, 128) : normalized;
    }

    private Integer normalizeStrength(Integer strength) {
        int safe = strength == null ? 3 : strength;
        return Math.max(1, Math.min(safe, 5));
    }

    private String normalizeDetailText(String detailText) {
        if (!StringUtils.hasText(detailText)) {
            return null;
        }
        String normalized = detailText.trim();
        return normalized.length() > 500 ? normalized.substring(0, 500) : normalized;
    }

    private String normalizeStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return null;
        }
        return status.trim().toUpperCase(Locale.ROOT);
    }
}

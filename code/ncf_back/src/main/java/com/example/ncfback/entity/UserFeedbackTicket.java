package com.example.ncfback.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserFeedbackTicket {
    private Long ticketId;
    private Long userId;
    private String username;
    private String preferDimension;
    private String preferValue;
    private String avoidDimension;
    private String avoidValue;
    private Integer preferenceStrength;
    private String detailText;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

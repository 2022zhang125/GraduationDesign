package com.example.ncfback.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserFollowEdge {
    private Long followerUserId;
    private Long followeeUserId;
    private String followeeUsername;
    private LocalDateTime createdAt;
}

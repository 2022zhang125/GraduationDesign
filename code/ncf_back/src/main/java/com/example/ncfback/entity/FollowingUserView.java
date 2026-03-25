package com.example.ncfback.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class FollowingUserView {
    private Long followerUserId;
    private Long followeeUserId;
    private String followeeUsername;
    private Integer followeeGender;
    private Integer followeeBirthYear;
    private LocalDateTime followTime;
}

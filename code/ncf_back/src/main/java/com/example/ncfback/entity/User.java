package com.example.ncfback.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class User {
    private Long userId;
    private String externalUserNo;
    @JsonIgnore
    private String passwordHash;
    private Integer gender;
    private Integer birthYear;
    private LocalDateTime registerTime;
    private Integer userStatus;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

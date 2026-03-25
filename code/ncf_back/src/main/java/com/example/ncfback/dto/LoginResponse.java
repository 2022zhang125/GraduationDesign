package com.example.ncfback.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class LoginResponse {
    private String token;
    private String tokenType;
    private long expireHours;
    private Long userId;
    private String username;
}

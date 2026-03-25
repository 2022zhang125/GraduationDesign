package com.example.ncfback.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class ApiResponse<T> {
    private int code;
    private String message;
    private T data;
    private LocalDateTime timestamp;

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(0, "success", data, LocalDateTime.now());
    }

    public static <T> ApiResponse<T> fail(String message) {
        return new ApiResponse<>(-1, message, null, LocalDateTime.now());
    }
}

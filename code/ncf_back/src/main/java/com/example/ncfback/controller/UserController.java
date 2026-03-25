package com.example.ncfback.controller;

import com.example.ncfback.dto.ApiResponse;
import com.example.ncfback.dto.PageResponse;
import com.example.ncfback.entity.User;
import com.example.ncfback.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping
    public ApiResponse<PageResponse<User>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(userService.page(page, size));
    }

    @GetMapping("/{userId}")
    public ApiResponse<User> detail(@PathVariable Long userId) {
        return ApiResponse.ok(userService.getById(userId));
    }
}

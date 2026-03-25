package com.example.ncfback.controller;

import com.example.ncfback.dto.ApiResponse;
import com.example.ncfback.dto.LoginRequest;
import com.example.ncfback.dto.LoginResponse;
import com.example.ncfback.dto.RegisterRequest;
import com.example.ncfback.dto.RegisterResponse;
import com.example.ncfback.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@RequestBody @Valid LoginRequest request) {
        return ApiResponse.ok(authService.login(request));
    }

    @PostMapping("/register")
    public ApiResponse<RegisterResponse> register(@RequestBody @Valid RegisterRequest request) {
        return ApiResponse.ok(authService.register(request));
    }
}

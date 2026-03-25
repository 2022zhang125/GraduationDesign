package com.example.ncfback.service;

import com.example.ncfback.config.JwtProperties;
import com.example.ncfback.dto.LoginRequest;
import com.example.ncfback.dto.LoginResponse;
import com.example.ncfback.dto.RegisterRequest;
import com.example.ncfback.dto.RegisterResponse;
import com.example.ncfback.entity.User;
import com.example.ncfback.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final JwtProperties jwtProperties;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuthenticationManager authenticationManager;
    private final UserService userService;

    public LoginResponse login(LoginRequest request) {
        String username = request.getUsername().trim();
        String password = request.getPassword();
        try {
            authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(username, password)
            );
        } catch (AuthenticationException ex) {
            throw new IllegalArgumentException("username or password is invalid");
        }

        User user = userService.getByUsername(username);
        String token = jwtTokenProvider.createToken(username, "USER");
        return new LoginResponse(token, "Bearer", jwtProperties.getExpireHours(), user.getUserId(), user.getExternalUserNo());
    }

    public RegisterResponse register(RegisterRequest request) {
        User created = userService.register(request);
        return new RegisterResponse(
                created.getUserId(),
                created.getExternalUserNo()
        );
    }
}

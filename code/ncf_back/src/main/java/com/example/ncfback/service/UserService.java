package com.example.ncfback.service;

import com.example.ncfback.dto.PageResponse;
import com.example.ncfback.dto.RegisterRequest;
import com.example.ncfback.entity.User;
import com.example.ncfback.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.time.Year;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public PageResponse<User> page(int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 100);
        int offset = (safePage - 1) * safeSize;

        List<User> users = userMapper.findPage(offset, safeSize);
        long total = userMapper.countAll();
        return new PageResponse<>(users, total, safePage, safeSize);
    }

    public User getById(Long userId) {
        User user = userMapper.findById(userId);
        if (user == null) {
            throw new IllegalArgumentException("User not found: " + userId);
        }
        return user;
    }

    public User getByUsername(String username) {
        String normalizedUsername = normalizeUsername(username);
        User user = userMapper.findByExternalUserNo(normalizedUsername);
        if (user == null) {
            throw new IllegalArgumentException("User not found: " + normalizedUsername);
        }
        return user;
    }

    public Long resolveUserId(String userKey) {
        if (!StringUtils.hasText(userKey)) {
            throw new IllegalArgumentException("userKey cannot be blank");
        }
        String normalized = userKey.trim();
        try {
            return Long.valueOf(normalized);
        } catch (NumberFormatException ex) {
            return getByUsername(normalized).getUserId();
        }
    }

    public synchronized User register(RegisterRequest request) {
        String externalUserNo = normalizeUsername(request.getExternalUserNo());
        if (userMapper.findByExternalUserNo(externalUserNo) != null) {
            throw new IllegalArgumentException("username already exists");
        }

        int currentYear = Year.now().getValue();
        if (request.getBirthYear() > currentYear) {
            throw new IllegalArgumentException("birthYear cannot be in the future");
        }

        Integer gender = request.getGender();
        if (gender == null || gender < 0 || gender > 2) {
            throw new IllegalArgumentException("gender must be 0, 1 or 2");
        }

        if (!StringUtils.hasText(request.getPassword())) {
            throw new IllegalArgumentException("password cannot be blank");
        }

        User user = new User();
        user.setUserId(userMapper.findNextUserId());
        user.setExternalUserNo(externalUserNo);
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setGender(gender);
        user.setBirthYear(request.getBirthYear());
        user.setUserStatus(1);

        LocalDateTime now = LocalDateTime.now();
        user.setRegisterTime(now);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);

        int affected = userMapper.insert(user);
        if (affected <= 0) {
            throw new IllegalArgumentException("register failed");
        }
        return user;
    }

    private String normalizeUsername(String username) {
        String normalized = username == null ? "" : username.trim();
        if (!StringUtils.hasText(normalized)) {
            throw new IllegalArgumentException("username cannot be blank");
        }
        return normalized;
    }
}

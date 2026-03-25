package com.example.ncfback.controller;

import com.example.ncfback.dto.ApiResponse;
import com.example.ncfback.entity.FollowingUserView;
import com.example.ncfback.service.UserFollowService;
import com.example.ncfback.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/follows")
@RequiredArgsConstructor
public class UserFollowController {

    private final UserFollowService userFollowService;
    private final UserService userService;

    @GetMapping("/users/{userId}")
    public ApiResponse<List<FollowingUserView>> list(@PathVariable String userId) {
        Long resolvedUserId = userService.resolveUserId(userId);
        return ApiResponse.ok(userFollowService.listByFollowerUserId(resolvedUserId));
    }

    @GetMapping("/users/{userId}/targets/{targetUserId}/exists")
    public ApiResponse<Boolean> exists(@PathVariable String userId, @PathVariable String targetUserId) {
        Long followerUserId = userService.resolveUserId(userId);
        Long followeeUserId = userService.resolveUserId(targetUserId);
        return ApiResponse.ok(userFollowService.exists(followerUserId, followeeUserId));
    }

    @PostMapping("/users/{userId}/targets/{targetUserId}")
    public ApiResponse<Boolean> add(@PathVariable String userId, @PathVariable String targetUserId) {
        Long followerUserId = userService.resolveUserId(userId);
        Long followeeUserId = userService.resolveUserId(targetUserId);
        userFollowService.addFollow(followerUserId, followeeUserId);
        return ApiResponse.ok(true);
    }

    @DeleteMapping("/users/{userId}/targets/{targetUserId}")
    public ApiResponse<Boolean> remove(@PathVariable String userId, @PathVariable String targetUserId) {
        Long followerUserId = userService.resolveUserId(userId);
        Long followeeUserId = userService.resolveUserId(targetUserId);
        userFollowService.removeFollow(followerUserId, followeeUserId);
        return ApiResponse.ok(true);
    }
}

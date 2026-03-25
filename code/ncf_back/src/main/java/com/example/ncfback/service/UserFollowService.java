package com.example.ncfback.service;

import com.example.ncfback.entity.FollowingUserView;
import com.example.ncfback.mapper.UserFollowMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserFollowService {

    private final UserFollowMapper userFollowMapper;
    private final UserService userService;
    private final RecommendationService recommendationService;

    public List<FollowingUserView> listByFollowerUserId(Long followerUserId) {
        userService.getById(followerUserId);
        return userFollowMapper.findByFollowerUserId(followerUserId);
    }

    public boolean exists(Long followerUserId, Long followeeUserId) {
        userService.getById(followerUserId);
        userService.getById(followeeUserId);
        return userFollowMapper.exists(followerUserId, followeeUserId) > 0;
    }

    @Transactional
    public void addFollow(Long followerUserId, Long followeeUserId) {
        validateFollowRequest(followerUserId, followeeUserId);
        if (userFollowMapper.exists(followerUserId, followeeUserId) > 0) {
            return;
        }
        int affected = userFollowMapper.insert(followerUserId, followeeUserId);
        if (affected <= 0) {
            throw new IllegalArgumentException("Add follow failed");
        }
        recommendationService.refreshUserRecommendations(followerUserId);
    }

    @Transactional
    public void removeFollow(Long followerUserId, Long followeeUserId) {
        validateFollowRequest(followerUserId, followeeUserId);
        userFollowMapper.delete(followerUserId, followeeUserId);
        recommendationService.refreshUserRecommendations(followerUserId);
    }

    private void validateFollowRequest(Long followerUserId, Long followeeUserId) {
        userService.getById(followerUserId);
        userService.getById(followeeUserId);
        if (followerUserId.equals(followeeUserId)) {
            throw new IllegalArgumentException("Cannot follow yourself");
        }
    }
}

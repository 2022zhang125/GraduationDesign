package com.example.ncfback.mapper;

import com.example.ncfback.entity.FollowingUserView;
import com.example.ncfback.entity.UserFollowEdge;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface UserFollowMapper {

    List<FollowingUserView> findByFollowerUserId(@Param("followerUserId") Long followerUserId);

    List<UserFollowEdge> findEdgesByFollowerUserId(@Param("followerUserId") Long followerUserId);

    List<UserFollowEdge> findAllEdges();

    int exists(@Param("followerUserId") Long followerUserId, @Param("followeeUserId") Long followeeUserId);

    int insert(@Param("followerUserId") Long followerUserId, @Param("followeeUserId") Long followeeUserId);

    int delete(@Param("followerUserId") Long followerUserId, @Param("followeeUserId") Long followeeUserId);

    long countByFollowerUserId(@Param("followerUserId") Long followerUserId);
}

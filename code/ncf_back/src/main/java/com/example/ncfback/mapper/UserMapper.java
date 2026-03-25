package com.example.ncfback.mapper;

import com.example.ncfback.entity.User;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface UserMapper {
    List<User> findPage(@Param("offset") int offset, @Param("limit") int limit);

    User findById(@Param("userId") Long userId);

    User findByExternalUserNo(@Param("externalUserNo") String externalUserNo);

    Long findNextUserId();

    int insert(User user);

    long countAll();
}

package com.example.ncfback.mapper;

import com.example.ncfback.entity.UserFeedbackTicket;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface UserFeedbackMapper {

    List<UserFeedbackTicket> findPageByUserId(@Param("userId") Long userId,
                                              @Param("status") String status,
                                              @Param("offset") int offset,
                                              @Param("limit") int limit);

    long countByUserId(@Param("userId") Long userId, @Param("status") String status);

    List<UserFeedbackTicket> findActiveByUserId(@Param("userId") Long userId);

    List<UserFeedbackTicket> findAllActive();

    long countActiveByUserId(@Param("userId") Long userId);

    int insert(UserFeedbackTicket ticket);

    int closeTicket(@Param("ticketId") Long ticketId, @Param("userId") Long userId);
}

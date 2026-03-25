package com.example.ncfback.mapper;

import com.example.ncfback.entity.FavoriteItemView;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface FavoriteMapper {
    List<FavoriteItemView> findByUserId(@Param("userId") Long userId);

    long countByUserId(@Param("userId") Long userId);

    List<Long> findItemIdsByUserId(@Param("userId") Long userId);

    int exists(@Param("userId") Long userId, @Param("itemId") Long itemId);

    int insert(@Param("userId") Long userId, @Param("itemId") Long itemId);

    int delete(@Param("userId") Long userId, @Param("itemId") Long itemId);
}

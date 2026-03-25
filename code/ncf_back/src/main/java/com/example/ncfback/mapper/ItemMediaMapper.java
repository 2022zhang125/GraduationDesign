package com.example.ncfback.mapper;

import com.example.ncfback.entity.ItemMedia;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface ItemMediaMapper {
    ItemMedia findByItemId(@Param("itemId") Long itemId);

    List<ItemMedia> findByItemIds(@Param("itemIds") List<Long> itemIds);

    int upsert(ItemMedia media);
}

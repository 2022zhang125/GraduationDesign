package com.example.ncfback.mapper;

import com.example.ncfback.entity.Item;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface ItemMapper {
    List<Item> findPage(@Param("genreCode") String genreCode,
                        @Param("offset") int offset,
                        @Param("limit") int limit);

    Item findById(@Param("itemId") Long itemId);

    Item findByExternalItemNo(@Param("externalItemNo") String externalItemNo);

    long countByGenre(@Param("genreCode") String genreCode);

    Item findByTitleAndArtist(@Param("title") String title,
                              @Param("artistName") String artistName);

    Long findNextItemId();

    int insert(Item item);
}

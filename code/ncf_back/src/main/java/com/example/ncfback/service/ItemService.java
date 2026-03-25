package com.example.ncfback.service;

import com.example.ncfback.dto.PageResponse;
import com.example.ncfback.entity.Item;
import com.example.ncfback.mapper.ItemMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ItemService {

    private final ItemMapper itemMapper;

    public PageResponse<Item> page(String genreCode, int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 100);
        int offset = (safePage - 1) * safeSize;

        List<Item> items = itemMapper.findPage(genreCode, offset, safeSize);
        long total = itemMapper.countByGenre(genreCode);
        return new PageResponse<>(items, total, safePage, safeSize);
    }

    public Item getById(Long itemId) {
        Item item = itemMapper.findById(itemId);
        if (item == null) {
            throw new IllegalArgumentException("音乐不存在: " + itemId);
        }
        return item;
    }
}

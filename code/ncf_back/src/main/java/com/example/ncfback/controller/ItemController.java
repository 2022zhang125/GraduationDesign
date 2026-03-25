package com.example.ncfback.controller;

import com.example.ncfback.dto.ApiResponse;
import com.example.ncfback.dto.PageResponse;
import com.example.ncfback.entity.Item;
import com.example.ncfback.service.ItemService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/items")
@RequiredArgsConstructor
public class ItemController {

    private final ItemService itemService;

    @GetMapping
    public ApiResponse<PageResponse<Item>> page(
            @RequestParam(required = false) String genre,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(itemService.page(genre, page, size));
    }

    @GetMapping("/{itemId}")
    public ApiResponse<Item> detail(@PathVariable Long itemId) {
        return ApiResponse.ok(itemService.getById(itemId));
    }
}

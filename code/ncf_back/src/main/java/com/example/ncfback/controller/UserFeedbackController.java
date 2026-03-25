package com.example.ncfback.controller;

import com.example.ncfback.dto.ApiResponse;
import com.example.ncfback.dto.FeedbackTicketCreateRequest;
import com.example.ncfback.dto.PageResponse;
import com.example.ncfback.entity.UserFeedbackTicket;
import com.example.ncfback.service.UserFeedbackService;
import com.example.ncfback.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/feedback")
@RequiredArgsConstructor
public class UserFeedbackController {

    private final UserFeedbackService userFeedbackService;
    private final UserService userService;

    @GetMapping("/users/{userId}")
    public ApiResponse<PageResponse<UserFeedbackTicket>> pageByUserId(
            @PathVariable String userId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        Long resolvedUserId = userService.resolveUserId(userId);
        return ApiResponse.ok(userFeedbackService.pageByUserId(resolvedUserId, status, page, size));
    }

    @PostMapping("/users/{userId}")
    public ApiResponse<UserFeedbackTicket> createTicket(
            @PathVariable String userId,
            @RequestBody FeedbackTicketCreateRequest request) {
        Long resolvedUserId = userService.resolveUserId(userId);
        return ApiResponse.ok(userFeedbackService.createTicket(resolvedUserId, request));
    }

    @DeleteMapping("/users/{userId}/tickets/{ticketId}")
    public ApiResponse<Boolean> closeTicket(@PathVariable String userId, @PathVariable Long ticketId) {
        Long resolvedUserId = userService.resolveUserId(userId);
        userFeedbackService.closeTicket(resolvedUserId, ticketId);
        return ApiResponse.ok(true);
    }
}

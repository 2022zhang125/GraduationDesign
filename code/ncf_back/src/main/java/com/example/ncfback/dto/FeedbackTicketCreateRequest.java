package com.example.ncfback.dto;

import lombok.Data;

@Data
public class FeedbackTicketCreateRequest {
    private String preferDimension;
    private String preferValue;
    private String avoidDimension;
    private String avoidValue;
    private Integer preferenceStrength;
    private String detailText;
}

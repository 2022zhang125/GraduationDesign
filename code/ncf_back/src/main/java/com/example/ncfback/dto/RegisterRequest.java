package com.example.ncfback.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {
    @NotBlank
    @Size(min = 4, max = 32)
    private String externalUserNo;

    @NotBlank
    @Size(min = 6, max = 64)
    private String password;

    @NotNull
    @Min(0)
    @Max(2)
    private Integer gender;

    @NotNull
    @Min(1900)
    @Max(2100)
    private Integer birthYear;
}

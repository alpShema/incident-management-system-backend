package com.amalitech.hilfe.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(String message, T data) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>("successful", data);
    }
}
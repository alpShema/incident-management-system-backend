package com.amalitech.hilfe.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Standard API envelope returned by all endpoints")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        @Schema(description = "Human-readable result message", example = "Incident created successfully") String message,
        @Schema(description = "Response payload — type varies by endpoint") T data
) {
    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(message, data);
    }
}

package com.amalitech.hilfe.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;

public record UpdateSeveritySlaRequest(
        @Min(value = 1, message = "Response time must be at least 1 second.")
        Integer responseTimeSeconds,
        @Min(value = 1, message = "Resolution time must be at least 1 second.")
        Integer resolutionTimeSeconds
) {
    @AssertTrue(message = "Please provide a response time or resolution time of at least 1 second.")
    private boolean isAtLeastOneFieldPresent() {
        return responseTimeSeconds != null || resolutionTimeSeconds != null;
    }
}

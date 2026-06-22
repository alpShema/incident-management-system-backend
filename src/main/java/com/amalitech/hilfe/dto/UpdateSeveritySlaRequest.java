package com.amalitech.hilfe.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;

public record UpdateSeveritySlaRequest(
        @Min(value = 1, message = "responseTimeSeconds must be at least 1")
        Integer responseTimeSeconds,
        @Min(value = 1, message = "resolutionTimeSeconds must be at least 1")
        Integer resolutionTimeSeconds
) {
    @AssertTrue(message = "At least one SLA threshold (responseTimeSeconds or resolutionTimeSeconds) must be provided with a value of 1 or greater")
    private boolean isAtLeastOneFieldPresent() {
        return responseTimeSeconds != null || resolutionTimeSeconds != null;
    }
}

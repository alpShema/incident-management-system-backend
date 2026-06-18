package com.amalitech.hilfe.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;

public record UpdateSeveritySlaRequest(
        @Min(value = 1, message = "responseTimeMinutes must be at least 1")
        Integer responseTimeMinutes,
        @Min(value = 1, message = "resolutionTimeMinutes must be at least 1")
        Integer resolutionTimeMinutes
) {
    @AssertTrue(message = "At least one SLA threshold (responseTimeMinutes or resolutionTimeMinutes) must be provided with a value of 1 or greater")
    private boolean isAtLeastOneFieldPresent() {
        return responseTimeMinutes != null || resolutionTimeMinutes != null;
    }
}

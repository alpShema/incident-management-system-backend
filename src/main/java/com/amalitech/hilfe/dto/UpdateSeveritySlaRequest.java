package com.amalitech.hilfe.dto;

import jakarta.validation.constraints.Min;

public record UpdateSeveritySlaRequest(
        @Min(value = 1, message = "responseTimeMinutes must be at least 1")
        Integer responseTimeMinutes,
        @Min(value = 1, message = "resolutionTimeMinutes must be at least 1")
        Integer resolutionTimeMinutes
) {
}

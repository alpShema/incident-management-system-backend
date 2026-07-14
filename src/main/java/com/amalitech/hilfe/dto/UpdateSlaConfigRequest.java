package com.amalitech.hilfe.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record UpdateSlaConfigRequest(
        @NotNull(message = "At-risk percentage is required.")
        @Min(value = 1, message = "At-risk percentage must be at least 1.")
        @Max(value = 99, message = "At-risk percentage must be at most 99.")
        Integer atRiskPct
) {
}

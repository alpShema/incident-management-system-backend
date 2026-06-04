package com.amalitech.hilfe.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record UpdateSlaConfigRequest(
        @NotNull(message = "atRiskPct is required")
        @Min(value = 1, message = "atRiskPct must be at least 1")
        @Max(value = 99, message = "atRiskPct must be at most 99")
        Integer atRiskPct
) {
}

package com.amalitech.hilfe.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Global SLA configuration")
public record SlaConfigResponse(
        @Schema(description = "Percentage of threshold remaining at which an incident becomes at-risk", example = "20")
        int atRiskPct
) {
}

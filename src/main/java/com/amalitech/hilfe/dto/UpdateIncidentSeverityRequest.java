package com.amalitech.hilfe.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Request body for updating the severity/priority of an incident")
public record UpdateIncidentSeverityRequest(
        @Schema(description = "Stable ID of the target severity level (e.g. Low, Moderate, High, Critical)", example = "sev-high")
        @NotBlank String severityId
) {}

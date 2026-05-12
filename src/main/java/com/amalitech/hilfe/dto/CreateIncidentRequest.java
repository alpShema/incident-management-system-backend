package com.amalitech.hilfe.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Request body for creating a new incident")
public record CreateIncidentRequest(
        @Schema(description = "Short summary of the incident", example = "Projector not working in Room 3B")
        @NotBlank String title,

        @Schema(description = "Detailed description of the issue", example = "The ceiling projector in Room 3B fails to power on after pressing the remote button.")
        @NotBlank String description,

        @Schema(description = "ID of the incident topic (type) selected during reporting", example = "topic-uuid")
        @NotBlank String incidentTypeId,

        @Schema(description = "ID of the location where the incident occurred", example = "location-uuid")
        @NotBlank String locationId,

        @Schema(description = "ID of the severity level (optional — defaults to system default if omitted)", example = "severity-uuid", nullable = true)
        String severityId
) {}

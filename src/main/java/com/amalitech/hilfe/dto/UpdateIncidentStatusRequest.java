package com.amalitech.hilfe.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Request body for updating the status of an incident. Valid lifecycle: Open → Pending → Resolved → Closed.")
public record UpdateIncidentStatusRequest(
        @Schema(description = "ID of the target status. Must be a valid next state in the lifecycle.", example = "status-uuid")
        @NotBlank String statusId
) {}

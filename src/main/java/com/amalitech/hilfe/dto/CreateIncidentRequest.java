package com.amalitech.hilfe.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(description = "Request body for creating a new incident")
public record CreateIncidentRequest(
        @Schema(description = "Short summary of the incident", example = "Projector not working in Room 3B")
        @NotBlank String title,

        @Schema(description = "Detailed description of the issue", example = "The ceiling projector in Room 3B fails to power on after pressing the remote button.")
        @NotBlank String description,

        @Schema(description = "Stable ID of the incident topic (type) selected during reporting", example = "type-account-issues")
        @NotBlank String incidentTypeId,

        @Schema(description = "Stable ID of the location where the incident occurred", example = "loc-accra")
        @NotBlank String locationId,

        @Schema(description = "Stable ID of the severity level (optional - defaults to system default if omitted)", example = "sev-low", nullable = true)
        String severityId,

        @Schema(description = "File attachments uploaded via presigned URLs (optional, max 5)", nullable = true)
        @Size(max = 5, message = "Maximum 5 attachments allowed")
        @Valid
        List<AttachmentRef> attachments
) {}

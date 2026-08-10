package com.amalitech.hilfe.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Request body for marking an incident as read or unread")
public record UpdateIncidentReadStatusRequest(
        @Schema(description = "True to mark the incident as read, false to mark it as unread", example = "false")
        @NotNull(message = "Please specify the read status.")
        Boolean read
) {}
